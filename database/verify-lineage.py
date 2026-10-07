"""Verify graph lineage against what each row records.

SHADOW rows store full bits AND lineage: FORCE-rebuild must reproduce the stored bits exactly.
Every lineage row (SHADOW or DELTA) records graph_hash at creation: sha256(rebuilt) must match it.
  verify-lineage.py <sample-size> [mw-port]     # samples lineage rows written in the last 2 days
"""
import hashlib, json, subprocess, sys, urllib.request

n = int(sys.argv[1]); port = sys.argv[2] if len(sys.argv) > 2 else "36000"
API = f"http://localhost:{port}/api/ramsey/graphs"
q = (f"select graph_id from graph where lineage_depth is not null and identified_date > now() - interval 2 day "
     f"order by rand() limit {n}")
ids = subprocess.run(["docker", "exec", "ramsey-db-mysql", "sh", "-c",
                      f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ramsey-dev -N -e "{q}"'],
                     capture_output=True, text=True, check=True).stdout.split()


def get(gid, mode):
    with urllib.request.urlopen(f"{API}/{gid}?reconstruct={mode}", timeout=60) as r:
        return json.load(r)


bad, depths, stored_checked = [], [], 0
for gid in ids:
    row, forced = get(gid, "none"), get(gid, "force")
    depths.append(row["lineageDepth"])
    if hashlib.sha256(forced["edgeData"].encode()).hexdigest() != row["graphHash"]:
        bad.append((gid, "hash"))
    if row["edgeData"] is not None:
        stored_checked += 1
        if row["edgeData"] != forced["edgeData"]:
            bad.append((gid, "bits"))
print(f"checked {len(ids)} rows (depth {min(depths, default=0)}..{max(depths, default=0)}), "
      f"{stored_checked} with stored bits; mismatches {len(bad)} {bad[:5]}")
print("VERDICT:", "PASS" if ids and not bad else "FAIL")
sys.exit(0 if ids and not bad else 1)
