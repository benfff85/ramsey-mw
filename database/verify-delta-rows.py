"""Rebuild every DELTA-stored graph (edge_data NULL, lineage_depth > 0) written since a timestamp and
check sha256(rebuilt) == graph_hash, the hash the QM recorded from the true bits at creation.
  verify-delta-rows.py 'YYYY-MM-DD HH:MM:SS' [limit]"""
import hashlib, json, subprocess, sys, urllib.request
since, limit = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 100000
q = (f"select graph_id from graph where edge_data is null and lineage_depth > 0 "
     f"and identified_date >= '{since}' order by graph_id limit {limit}")
ids = subprocess.run(["docker", "exec", "ramsey-db-mysql", "sh", "-c",
                      f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -D ramsey-dev -e "{q}"'],
                     capture_output=True, text=True, check=True).stdout.split()
bad, depths = [], []
for gid in ids:
    with urllib.request.urlopen(f"http://localhost:36000/api/ramsey/graphs/{gid}", timeout=60) as r:
        g = json.load(r)
    depths.append(g["lineageDepth"])
    if not g["edgeData"] or hashlib.sha256(g["edgeData"].encode()).hexdigest() != g["graphHash"]:
        bad.append(gid)
print(f"delta rows rebuilt: {len(ids)} (depth {min(depths, default=0)}..{max(depths, default=0)}), hash mismatches: {len(bad)} {bad[:5]}")
print("VERDICT:", "PASS" if ids and not bad else "FAIL")
sys.exit(0 if ids and not bad else 1)
