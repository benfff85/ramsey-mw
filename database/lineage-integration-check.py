"""Prove the new middleware's lineage handling against a real MySQL: the restore-drill copy.

Builds a synthetic chain through the API (one snapshot, then 1,200 deltas with snapshots every
500, including repeated edges), then reads every graph back with reconstruct=stored and =force and
checks the bits and their SHA-256 against what was written. Also checks the derive endpoint against
a rebuilt base, and that a broken chain returns 409.
"""
import hashlib, json, random, sys, urllib.error, urllib.request

API = f"http://localhost:{sys.argv[1]}/api/ramsey/graphs"
N = 282
E = N * (N - 1) // 2


def call(method, url, body=None):
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body else None,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def idx(a, b):
    a, b = min(a, b), max(a, b)
    return a * (2 * N - a - 1) // 2 + (b - a - 1)


rng = random.Random(7)
bits = [rng.choice("01") for _ in range(E)]
truth = {}
root = call("POST", API, {"vertexCount": N, "subgraphSize": 8, "cliqueCount": 99, "edgeData": "".join(bits),
                          "graphHash": hashlib.sha256("".join(bits).encode()).hexdigest(), "lineageDepth": 0})
truth[root["graphId"]] = "".join(bits)
parent, depth = root["graphId"], 0
for i in range(1, 1201):
    a, b = rng.randrange(N), rng.randrange(N)
    while b == a:
        b = rng.randrange(N)
    edges = [(a, b)] if i % 7 else [(a, b), (b, a)]          # every 7th is a self-cancelling pair
    for x, y in edges:
        k = idx(x, y)
        bits[k] = "1" if bits[k] == "0" else "0"
    s = "".join(bits)
    depth = 0 if i % 500 == 0 else depth + 1
    flips = "{" + ",".join(f"{{{x}:{y}}}" for x, y in edges) + "}"
    g = call("POST", API, {"vertexCount": N, "subgraphSize": 8, "cliqueCount": 99, "parentGraphId": parent,
                           "flippedEdges": flips, "lineageDepth": depth,
                           "graphHash": hashlib.sha256(s.encode()).hexdigest(),
                           "edgeData": s if depth == 0 else None})
    truth[g["graphId"]] = s
    parent = g["graphId"]

bad = 0
for gid, s in truth.items():
    for mode in ("stored", "force"):
        got = call("GET", f"{API}/{gid}?reconstruct={mode}")
        if got["edgeData"] != s or hashlib.sha256(got["edgeData"].encode()).hexdigest() != got["graphHash"]:
            bad += 1
            print("MISMATCH", gid, mode)
none = call("GET", f"{API}/{parent}?reconstruct=none")
derived = call("GET", f"{API}/{parent}?edgesToFlip=%7B%7B0:1%7D%7D")
expect = list(truth[parent]); k = idx(0, 1); expect[k] = "1" if expect[k] == "0" else "0"
orphan = call("POST", API, {"vertexCount": N, "subgraphSize": 8, "cliqueCount": 1, "parentGraphId": 2_000_000_000,
                            "flippedEdges": "{{0:1}}", "lineageDepth": 1})
try:
    call("GET", f"{API}/{orphan['graphId']}")
    broken = "NO ERROR (bad)"
except urllib.error.HTTPError as e:
    broken = str(e.code)
print(f"graphs {len(truth)}, mismatches {bad}, none-mode edgeData null: {none['edgeData'] is None}, "
      f"derive ok: {derived['edgeData'] == ''.join(expect)}, broken chain -> {broken}")
print("VERDICT:", "PASS" if bad == 0 and none["edgeData"] is None and derived["edgeData"] == "".join(expect) and broken == "409" else "FAIL")
