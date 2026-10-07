#!/bin/bash
# Consistent logical backup of `ramsey-dev` while the fleet keeps running (InnoDB + --single-transaction),
# plus immutable-aggregate checksums so a restore can be verified exactly.
#   ./backup-mysql.sh <output-dir>
set -euo pipefail
OUT="${1:?output dir}"; mkdir -p "$OUT"
DB=ramsey-db-mysql; SCHEMA=ramsey-dev
docker exec "$DB" sh -c 'command -v mysqldump' >/dev/null || { echo "mysqldump missing in $DB"; exit 1; }
# High-water marks first: rows at or below them are immutable apart from stage.status/updated_date
# and graph.edge_data (prune), so the aggregates below must match a restore exactly.
q() { docker exec "$DB" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -D $SCHEMA -e \"$1\"" 2>/dev/null; }
q "select 'graph_max', max(graph_id) from graph; select 'stage_max', max(stage_id) from stage;" > "$OUT/hwm.txt"
GM=$(awk '$1=="graph_max"{print $2}' "$OUT/hwm.txt"); SM=$(awk '$1=="stage_max"{print $2}' "$OUT/hwm.txt")
time docker exec "$DB" sh -c "mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --quick \
  --routines --triggers --events --hex-blob --set-gtid-purged=OFF --databases $SCHEMA" 2>/dev/null \
  | zstd -T0 -10 -q -o "$OUT/mysql.sql.zst"
q "select 'graph', count(*), sum(clique_count), min(graph_id) from graph where graph_id <= $GM;
   select 'stage', count(*), sum(base_graph_id), sum(campaign_id) from stage where stage_id <= $SM;
   select 'campaign', count(*), sum(campaign_id) from campaign;
   select 'fleet', count(*) from fleet;" > "$OUT/mysql-checksums.txt"
ls -la "$OUT/mysql.sql.zst"; cat "$OUT/hwm.txt" "$OUT/mysql-checksums.txt"
