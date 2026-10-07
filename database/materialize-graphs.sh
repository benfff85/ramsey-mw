#!/bin/bash
# Prerequisite for rolling the middleware back (fallback L2): write full edge data onto every delta
# graph the old stack can read: each ACTIVE stage's base and the last 5,000 stage bases per
# campaign (CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT). Incumbents and kick seeds are already snapshots.
#   ./materialize-graphs.sh [--dry-run]
set -euo pipefail
DRY="${1:-}"
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
