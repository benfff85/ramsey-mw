#!/bin/bash
# Prerequisite for rolling the middleware back (fallback L2), and ONLY after the QM is back on OFF
# (checked below): write full edge data onto every delta
# graph the old stack can read: each ACTIVE stage's base and the last 5,000 stage bases per
# campaign (CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT). Incumbents and kick seeds are already snapshots.
#   ./materialize-graphs.sh [--dry-run]
set -euo pipefail
DRY="${1:-}"
QM_CONTAINER="${QM_CONTAINER:-ramsey-ramsey-queue-manager-1}"
# L2 is only safe with the QM back on OFF (fallback L0/L1) AND at least one OFF advance since: the
# rollback middleware ignores lineage fields, so a SHADOW/DELTA QM's next POST would be stored with
# no bits and no lineage -- a graph nobody can rebuild, invisible until a QM restart lands on it.
mode="$(docker inspect "$QM_CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^GRAPH_STORAGE_MODE=//p' | head -1)"
if [ "${mode:-OFF}" != "OFF" ]; then
  echo "refusing: $QM_CONTAINER runs GRAPH_STORAGE_MODE=$mode. Set it to OFF and recreate the QM first (fallback L0/L1)." >&2
  exit 2
fi
newest_has_lineage=$(docker exec ramsey-db-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ramsey-dev -N -e "
  select g.lineage_depth is not null from stage s join graph g on g.graph_id = s.base_graph_id order by s.stage_id desc limit 1"' 2>/dev/null)
if [ "$newest_has_lineage" != "0" ]; then
  echo "refusing: the newest stage's base still carries lineage; wait for one OFF-mode advance, then re-run." >&2
  exit 2
fi
IDS=$(docker exec ramsey-db-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ramsey-dev -N -e "
  select distinct g.graph_id from graph g join (
    select base_graph_id from stage where status = \"ACTIVE\"
    union select base_graph_id from (select s.base_graph_id, row_number() over (partition by s.campaign_id order by s.stage_id desc) rn
                                     from stage s join stage a on a.campaign_id = s.campaign_id and a.status = \"ACTIVE\") t where rn <= 5000
  ) need on need.base_graph_id = g.graph_id
  where g.edge_data is null and g.lineage_depth is not null"' 2>/dev/null)
echo "delta graphs to materialize: $(echo "$IDS" | grep -c . || true)"
[ "$DRY" = "--dry-run" ] && exit 0
n=0; for id in $IDS; do
  curl -sf -X PUT "http://localhost:36000/api/ramsey/graphs/$id/materialize" >/dev/null && n=$((n+1)); done
echo "materialized $n"
left=$(docker exec ramsey-db-mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" ramsey-dev -N -e \"select count(*) from graph where graph_id in ($(echo $IDS | tr ' ' ',' | sed 's/^$/0/')) and edge_data is null\"" 2>/dev/null)
echo "still missing: $left"; [ "$left" = "0" ]
