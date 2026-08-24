#!/bin/bash
#
# Null `graph.edge_data` for graphs outside the retention set.
#
# `graph` grows without bound: every stage advance writes a 39,621-character bitstring, and at the
# current cadence that is tens of thousands of rows a day. Nothing reads a superseded stage's
# bitstring, but `clique_count` is the search trajectory the UI plots and must survive -- so this
# NULLs `edge_data` rather than deleting rows. See database/README.md for the full rationale, the
# retention rules, and the run log.
#
# Safe to run repeatedly: with nothing outside the retention set it nulls 0 rows and exits.
#
#   ./prune-graph-edge-data.sh --dry-run   # report what would happen, change nothing
#   ./prune-graph-edge-data.sh             # prune
#
set -euo pipefail

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

DB_CONTAINER="${DB_CONTAINER:-ramsey-db-mysql}"
MW_CONTAINER="${MW_CONTAINER:-ramsey-ramsey-mw-1}"
SCHEMA="${SCHEMA:-ramsey-dev}"
DB_USER="${DB_USER:-ramsey-user-dev}"
BATCH="${BATCH:-50000}"
MAX_BATCHES="${MAX_BATCHES:-200}"
# 4x CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT, the queue manager's reseed window.
KEEP_STAGES="${KEEP_STAGES:-20000}"
# Graphs newer than MAX(graph_id) - HWM_MARGIN are never touched. See "the guard" below.
HWM_MARGIN="${HWM_MARGIN:-1000}"

log() { echo "[$(date -u +%FT%TZ)] $*"; }
die() { log "ABORT: $*"; exit 1; }

# launchd starts jobs with a bare PATH that does not include /usr/local/bin, where the docker CLI
# lives. Without this the scheduled run fails and the interactive run succeeds, which is the most
# annoying way for a cron job to be broken.
PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"

# One run at a time. A prune that overlaps itself would rebuild the retention set mid-flight and
# recompute the high-water mark, losing the guarantee the fixed mark exists to provide.
#
# `mkdir` rather than `flock`, which macOS does not ship: mkdir is atomic on POSIX and needs no
# extra tooling. A lock older than six hours is treated as abandoned by a killed run -- the longest
# prune observed is under a minute, so six hours cannot be a live run.
LOCK_DIR="${TMPDIR:-/tmp}/ramsey-graph-prune.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  if [ ! -d "$LOCK_DIR" ]; then
    # Not a directory at all -- a leftover from an older locking scheme, not a running prune.
    log "clearing non-directory at $LOCK_DIR"
    rm -f "$LOCK_DIR"
  elif [ -n "$(find "$LOCK_DIR" -maxdepth 0 -mmin +360 2>/dev/null)" ]; then
    log "removing stale lock $LOCK_DIR"
    rmdir "$LOCK_DIR" 2>/dev/null || true
  else
    die "another prune is already running (lock: $LOCK_DIR)"
  fi
  mkdir "$LOCK_DIR" 2>/dev/null || die "could not take lock $LOCK_DIR"
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || die "$DB_CONTAINER is not running"
docker inspect "$MW_CONTAINER" >/dev/null 2>&1 || die "$MW_CONTAINER is not running (needed for credentials)"

# Read the password out of the running middleware rather than storing it anywhere. Never echoed.
MYSQL_PWD="$(docker inspect "$MW_CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | sed -n 's/^DB_PASS=//p' | head -1)"
[ -n "$MYSQL_PWD" ] || die "could not read DB_PASS from $MW_CONTAINER"
export MYSQL_PWD

sql()  { docker exec -e MYSQL_PWD="$MYSQL_PWD" "$DB_CONTAINER" mysql -u"$DB_USER" -N -e "$1"; }
sqlv() { docker exec -e MYSQL_PWD="$MYSQL_PWD" "$DB_CONTAINER" mysql -u"$DB_USER" -e "$1"; }

# data_free reads 0 until statistics refresh, so this must come before any decision based on it.
sql "ANALYZE TABLE \`$SCHEMA\`.graph;" >/dev/null
before="$(sql "select concat(round(data_length/1073741824,2),' GB data, ',round(data_free/1073741824,2),' GB free')
  from information_schema.tables where table_schema='$SCHEMA' and table_name='graph';")"
log "before: $before"

log "building retention set"
sql "use \`$SCHEMA\`;
DROP TABLE IF EXISTS keep_graphs;
CREATE TABLE keep_graphs (graph_id INT PRIMARY KEY);
-- 1. base graphs of the most recent stages
INSERT IGNORE INTO keep_graphs
  SELECT base_graph_id FROM (SELECT base_graph_id FROM stage ORDER BY stage_id DESC LIMIT $KEEP_STAGES) t
  WHERE base_graph_id IS NOT NULL;
-- 2. every perturbation kick seed
INSERT IGNORE INTO keep_graphs
  SELECT base_graph_id FROM stage WHERE details LIKE '%PERTURBATION kick%' AND base_graph_id IS NOT NULL;
-- 3. every ACTIVE stage's base graph
INSERT IGNORE INTO keep_graphs
  SELECT base_graph_id FROM stage WHERE status='ACTIVE' AND base_graph_id IS NOT NULL;
-- 4. each campaign's incumbent (minimum clique_count)
INSERT IGNORE INTO keep_graphs
  SELECT g.graph_id FROM graph g
  JOIN (SELECT s.campaign_id, MIN(g2.clique_count) mc FROM stage s
        JOIN graph g2 ON g2.graph_id = s.base_graph_id GROUP BY s.campaign_id) m
    ON g.clique_count = m.mc
  JOIN stage s2 ON s2.base_graph_id = g.graph_id AND s2.campaign_id = m.campaign_id;"
log "retention set: $(sql "select count(*) from \`$SCHEMA\`.keep_graphs;") graphs"

# THE GUARD -- not optional. The retention set is a snapshot, but stages keep advancing while the
# batched UPDATE runs. On 2026-07-30 a prune without this nulled the then-active stage's base graph
# and broke queue-manager progression until the chain advanced past it.
#
# Computed ONCE and held for the whole run: every graph created during the prune sits above this
# mark and is therefore protected for the entire run. Recomputing per batch would only protect the
# newest $HWM_MARGIN at each instant, which is a weaker guarantee the longer the run takes.
HWM="$(sql "select max(graph_id) - $HWM_MARGIN from \`$SCHEMA\`.graph;")"
log "fixed high-water mark: $HWM (graphs at or above it are never touched)"

eligible="$(sql "select count(*) from \`$SCHEMA\`.graph g
  where edge_data is not null and graph_id < $HWM
    and not exists (select 1 from \`$SCHEMA\`.keep_graphs k where k.graph_id = g.graph_id);")"
log "eligible to null: $eligible"

if [ "$DRY_RUN" = "1" ]; then
  log "DRY RUN — no changes made"
  sql "use \`$SCHEMA\`; DROP TABLE keep_graphs;"
  exit 0
fi

total=0
for i in $(seq 1 "$MAX_BATCHES"); do
  n="$(sql "use \`$SCHEMA\`;
    UPDATE graph g SET edge_data = NULL
    WHERE edge_data IS NOT NULL AND graph_id < $HWM
      AND NOT EXISTS (SELECT 1 FROM keep_graphs k WHERE k.graph_id = g.graph_id)
    LIMIT $BATCH;
    SELECT ROW_COUNT();")"
  n="${n:-0}"
  [ "$n" -eq 0 ] && break
  total=$((total + n))
  log "  batch $i: $n rows (total $total)"
done

sql "use \`$SCHEMA\`; DROP TABLE keep_graphs;"
sql "ANALYZE TABLE \`$SCHEMA\`.graph;" >/dev/null
after="$(sql "select concat(round(data_length/1073741824,2),' GB data, ',round(data_free/1073741824,2),' GB free')
  from information_schema.tables where table_schema='$SCHEMA' and table_name='graph';")"
log "after: $after"
log "nulled $total rows"

# The failure this is guarding against is silent, so check for it rather than assume it.
orphans="$(sql "select count(*) from \`$SCHEMA\`.stage s
  join \`$SCHEMA\`.graph g on g.graph_id = s.base_graph_id
  where g.edge_data is null
    and (s.status = 'ACTIVE' or s.stage_id > (select max(stage_id) - $KEEP_STAGES from \`$SCHEMA\`.stage));")"
if [ "$orphans" != "0" ]; then
  log "WARNING: $orphans live stages now have a NULL base graph — investigate immediately"
  exit 1
fi
log "verified: no live stage lost its base graph"
