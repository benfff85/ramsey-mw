# Proposal: clique-guided perturbation (kick by edge participation, not uniformly at random)

**Status: PROPOSED, not built.**
**Date:** 2026-07-31
**Expected gain:** unknown, and honestly might be zero — see §9. What is certain is that the *kick
magnitude* axis is exhausted, so this is the cheapest untried change of kick **shape**.
**Risk profile:** unlike the hoist, this is **not** an exact change — it alters which graphs the
search visits. A bug here does not produce wrong numbers, it produces a worse search, which is
slower to detect. §7 defines the acceptance criterion up front for that reason.
**Blast radius:** queue manager only. No worker, middleware, database, or UI change.

This document is self-contained. It states the measurement that motivates the change, the proposed
change, exactly where the code goes, the two traps found while scoping it, and what would kill it.

---

## 1. The measurement that motivates this

Campaign 10 has run 22 perturbation kicks since stage 273,148. As of stage ~491,934:

- **218,786 stages** elapsed since the first kick.
- **Best result in all of it: 25,705.**
- The incumbent, **25,604 (graph 276750), was found at stage 267,359 — before the entire kick
  sequence.** Not one kick has improved on it.

Epoch outcome is **bimodal**. An epoch either returns to ~1.0× the incumbent or strands on a
~785K shelf. Nothing lands in between:

| kick size | kicks | recovered to ~1.0× | stranded at 30–53× |
|---|---|---|---|
| 960 pairs (x16) | 1 | 1 | 0 |
| 1920 pairs (x32) | 16 | **15** (floors 25,705–25,796, in ~5–9k stages) | 1 |
| 3840 pairs (x64) | 5 | **0** | 5 (floors 782k–1.35M, given 17–27k stages) |

A 3840-pair kick peaks at ~4.6M mono-8-cliques. A uniformly random 282-coloring has expectation
C(282,8) · 2 · 2⁻²⁸ ≈ **6.7M**, so an x64 kick does not perturb the incumbent — it discards it, and
the descent then finds the generic shelf that any random start finds. The five completed stranded
epochs burned **107,630 stages, 49% of everything since the first kick**, with a sixth still
running. That is what the x32 cap revert (mw #184) fixed.

### 1.1 Why this indicts the whole size axis, not just x64

Reverting the cap stops the waste but cannot produce a better graph, because **both** ends of the
size axis are already characterised:

- **Small kicks return to the same basin.** All 15 healthy x32 epochs floor in 25,705–25,796 — a
  91-unit band sitting *just above* the incumbent, which none of them reaches, let alone passes.
- **Large kicks land in random-land** and strand.

There is no magnitude that lands somewhere *new and good*. Fifteen independent perturbations of the
incumbent converging into a 91-unit band above it is not a sampling artefact; it says the uniform
random kick, at any strength that preserves the incumbent's structure, re-enters the basin it just
left. So the remaining lever is **which edges get flipped**, not how many.

### 1.2 What is explicitly *not* the problem

Recorded because it was my first hypothesis and it was wrong, and the wrong version is more
plausible-sounding than the right one:

The basin-staleness clock is **not** malfunctioning. Trailing stalls in the stranded epochs measured
**1,019 / 1,004 / 796 / 1,017** stages — every one went properly stale and fired on schedule. The
descent toward the shelf consists of 16–26k stages of *genuine* new minima, so staleness cannot
detect doom early: **a doomed descent and a healthy one look identical to it.** Do not "fix" the
clock. See §6.3 for why the incumbent-relative guard that would distinguish them was also rejected.

---

## 2. The proposed change in one sentence

Instead of choosing the kicked edges uniformly at random, choose them with probability biased by how
many monochromatic 8-cliques each edge currently participates in.

The intuition: the incumbent's 25,604 cliques are not spread evenly over the 39,621 edges. A uniform
kick spends most of its flips on edges that participate in few or no cliques — structurally
irrelevant churn that the descent immediately undoes, which is a mechanism consistent with 15
epochs returning to the same 91-unit band. Biasing toward high-participation edges perturbs the part
of the graph that actually carries the objective.

**This intuition is a hypothesis, not a result.** §9 lists what would falsify it.

---

## 3. Where the code goes

All of it is in `ramsey-queue-manager`.

| what | where | size |
|---|---|---|
| per-edge participation counts | `qm/utility/CliqueCounter.java` (126 lines today) | the only non-trivial piece — §4 |
| biased edge selection | `StageProgressionMonitor.perturbBalanced` (~20 lines) | small — §5 |
| selection-mode config | `RamseyConfig.Perturbation` | trivial |
| kick provenance in `details` | `perturbAndAdvance` | one string |

Unchanged: `ramsey-worker-rust`, `ramsey-mw`, the database schema, and `ramsey-ui`. The UI's ILS card
parses the kick `details` marker (`PERTURBATION kick from graph N (count), pairs=P`); appending a
mode field is backward-compatible with its existing parser, but recording the mode there is what
makes the A/B in §7 readable straight off the dashboard.

---

## 4. The hard part: `CliqueCounter` cannot currently name the edges in a clique

`CliqueCounter` is a Java port of the worker's kernel and already runs once per kick to stamp the
perturbed graph's true `clique_count`. It totals cliques but **cannot say which edges they contain**,
for two deliberate reasons:

1. **It tracks no `R` set.** The traversal is `bk(depth, p, adjacency, cliqueSize, words)` — duplicate
   prevention comes from the shrinking candidate set, so the committed vertices are never
   materialised. There is nothing to attribute *to*.
2. **The last two levels never enumerate.** At `depth == cliqueSize - 1` it returns
   `cardinality(p)`; at `depth == cliqueSize - 2` it returns a sum of `intersectCardinality(...)`
   per candidate. Both are popcounts — cliques are counted wholesale and their members never exist.

This is precisely the leaf-shortcut / k-2-inline optimisation that bought ~1.40× in the Rust kernel,
so it is not a defect to remove; it just means participation needs a **separate, non-shortcut
traversal**.

### 4.1 It is cheaper than it looks

Attribution does not need a rewrite. At the `k-2` level the information is already in hand: `v` is
enumerated explicitly, and the completing vertices are exactly the set bits of `p ∩ adj(v)`. So:

- thread an `int[cliqueSize]` of committed vertices through the recursion;
- at the bottom, iterate that word instead of popcounting it;
- for each completed clique, increment the C(8,2) = **28** edge counters for its vertex pairs.

Cost is one extra full enumeration per kick, without the shortcut. The base count is "low seconds"
(the class's own doc comment, with a `do NOT put this on any hot path` warning that this respects —
it stays once-per-kick). Losing the shortcut costs roughly the 1.40× it bought. The per-clique work
is trivial: at the incumbent, 25,604 cliques × 28 = ~717k increments. **Call it single-digit seconds,
against kicks that are 5,000–20,000 stages apart.** Negligible, and off every hot path.

Implement it as a *separate method* (`countMonoCliquesPerEdge`) rather than by parameterising `bk`,
so the existing fast total-count path stays byte-for-byte what it is today.

### 4.2 Validation

The two counters must agree: summing the per-edge participation array and dividing by 28 must equal
`countMonoCliques` exactly, for every graph tested — each 8-clique contributes exactly 28 edge
increments. That is a cheap, exact, self-checking invariant and should be the unit test, run against
the incumbent and against several kicked graphs.

---

## 5. Selection, and the balance invariant

`perturbBalanced` today builds `redIdx` / `blueIdx`, calls `Collections.shuffle` on each, and flips
the first `p` of each — flipping equal numbers red→blue and blue→red to preserve the engine's
balance invariant (currently 19,811 red / 19,810 blue).

The change replaces the two shuffles with participation-weighted selection **within each colour
list separately**. Balance preservation then falls out for free — the counts flipped in each
direction are still equal, only the choice of which changes.

---

## 6. Two traps found while scoping

### 6.1 The hoist per-edge table cannot be reused

This is the obvious shortcut — the fleet *already* computes per-edge counts and publishes them to
Redis, so why recompute in Java? Checked against the live instance, and it does not work:

- **Keys are `hoist_shard:{graphId}:{slice}` with a ~22-second TTL.** They exist only for the graph
  currently being swept. The incumbent's table is long gone by kick time — and the kick is applied to
  the *incumbent*, which is generally not the active stage's base graph.
- **It stores the wrong quantity.** The table holds `C_b` / `D_r`: the cliques that flipping that
  edge would **create** in the opposite colour. Participation is how many cliques the edge is in
  *now*. Related but not equal, and not derivable from each other without the counts this proposal
  computes anyway.

Wrong graph, wrong quantity, expired. Recompute in the QM.

### 6.2 A greedy kick is likely an expensive no-op

**This is the most important design decision in the proposal.** Flipping the *highest*-participation
edges is close to what the descent already does — it is approximately a steepest-descent move — so
a greedy kick can walk straight back into the basin it just left, which is the exact failure mode of
the current uniform kick, reproduced at higher cost.

Bias, do not maximise. Two candidate policies, both cheap:

- **Roulette:** select edge *e* with probability ∝ `participation(e)^α`. `α = 0` reproduces today's
  uniform kick exactly, which makes it a clean A/B control and a safe rollback.
- **Top-quantile:** sample uniformly from the top *q* fraction by participation.

Make the policy and its strength config knobs (`PERTURBATION_SELECTION_MODE`,
`PERTURBATION_SELECTION_ALPHA`). Being able to dial continuously back to the current behaviour is
what makes this A/B-able rather than a one-way door.

### 6.3 The incumbent-relative guard, and why it is not here

An earlier iteration of this work proposed abandoning a basin that cannot get within N× of the
incumbent. The measurement killed it and it should not be revived without new data: a **healthy**
basin sits at **64–68×** the incumbent at stage 500 and **51–61×** at stage 1000 (the drop to 1.0×
comes late, over ~5–9k stages), versus 68.3–80.1× for stranded ones. That leaves a ~12% margin at
stage 1000 on n = 15/6 — too thin to trust, and with x64 gone it would only ever catch the 1-in-16
x32 case. Recorded so the "obvious" guard is not re-derived a third time.

---

## 7. Acceptance criterion — decide this before writing code

This change alters which graphs get visited, so it cannot be validated by equality against a
baseline the way the hoist was. Judge it exactly the way `STAGE_ADOPT_SETTLE_MS` is judged:

> **Floor reached per kick cycle. Never stage rate.**

Concretely: run ≥ 8 kick cycles with `α > 0` and compare the distribution of epoch floors against
the 15 healthy uniform epochs already on record (25,705–25,796, mean ≈ 25,74x). That existing band
is an unusually good control — same campaign, same incumbent, same engine, 15 samples.

- **Win:** any epoch floor **< 25,604**. That is the whole point, and nothing else counts as success.
- **Encouraging:** the floor distribution shifts down materially versus the 25,705–25,796 band,
  even without breaking the incumbent — evidence the mechanism does something.
- **Kill:** floors indistinguishable from the uniform band after ~8 cycles, or any epoch stranding
  (which would mean the bias is acting as a *bigger* kick rather than a smarter one).

At ~5–9k stages per healthy cycle, 8 cycles is roughly 40–70k stages. Budget for it in stages, not
wall-clock, per the project's standing rule.

---

## 8. What would kill this, honestly

The project's track record with new move classes is poor and should be priced in: SA, tabu, and VDS
were all retired at zero improvements; single-vertex row optimisation and 2-vertex joint re-opt both
came back empty on graph 8348; ~1.1M exact MaxSAT windows to |F| = 128 found zero escapes. Seven
multi-seed basins never went below 25,840, and the current incumbent 25,604 came from a kick
sequence that has since produced nothing in 218,786 stages.

The specific ways this fails:

1. **Participation is roughly uniform.** If the 25,604 cliques are spread evenly across the 39,621
   edges, the weighting is a no-op by construction. *This is measurable in an afternoon and should be
   checked FIRST* — compute the participation histogram on graph 276750 before writing any
   selection code. A flat histogram kills the proposal for the cost of one `CliqueCounter` change.
2. **High-participation edges are exactly the ones the descent re-flips**, so the kick is undone
   immediately (§6.2). Mitigated but not eliminated by roulette rather than argmax.
3. **The basin is genuinely deep** and no perturbation of *any* shape escapes it, which is what the
   deep-floor conclusion in `multiseed-basin-program-results.md` already suggests. In that case this
   is another confirmation, not a breakthrough.

Failure mode 1 is cheap to test and eliminates the largest chunk of risk, so **§8.1 is the first
task, not the last.**

### 8.1 Recommended first step (half a day, no search impact)

Extend `CliqueCounter` per §4, run it once against graph 276750, and plot the participation
histogram. Three outcomes:

- **Heavy-tailed** (a minority of edges carry most cliques) → the premise holds, build §5.
- **Flat** → stop; write up the null result and close the proposal.
- **Structured** (e.g. participation concentrated on particular vertices) → more interesting than
  either, and would suggest a *vertex*-oriented kick instead. Note that the single-vertex move class
  is already proven exhausted on graph 8348, so this would need care not to re-run a closed
  experiment.

This step touches no running service and can be done in `single-flip-check/` against a live graph
export, keeping production app code untouched per the standing constraint.

---

## 9. Summary

- The kick **magnitude** axis is exhausted: small kicks re-enter the same basin (15 epochs into a
  91-unit band above the incumbent), large ones strand in random-land (5 of 5).
- Kick **shape** is untried, and clique participation is the natural first shape to try.
- The change is queue-manager-only; the single piece of real work is teaching `CliqueCounter` to
  attribute cliques to edges, which the leaf-popcount shortcut currently prevents (§4).
- Two traps: the hoist table looks reusable and is not (§6.1), and a greedy selection is likely a
  no-op (§6.2).
- The premise is falsifiable for ~half a day of work *before* any search-affecting code is written
  (§8.1). Do that first.
