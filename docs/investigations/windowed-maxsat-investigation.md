# Windowed Complete-MaxSAT Investigation (Phases 1–3)

**Date:** 2026-06-27
**Author:** Ben Ferenchak + Claude
**Status:** Tooling built, validated (opus-reviewed), and run; both target graphs EXACT-locked. High-volume runs continuing.
**Realizes:** the "complete MaxSAT/SAT solver on a window" and "k ≥ 3 windowed MaxSAT" next-steps from `search-status-2026-06-14.md` (06-19 update, items #1 and #3).

---

## Why this is qualitatively different from everything before it

Every prior local-search method — the production engine, simulated annealing, tabu, VDS, single-vertex row-opt (`rowopt_pilot`), and 2-vertex joint re-opt (`joint_pilot`, SATLike/WalkSAT) — is **incomplete** local search. It can find an improving move but can only ever report *"our heuristic didn't find one."* It cannot prove a move is absent.

A **complete** MaxSAT solver on a bounded window returns a **proven optimum**: it either finds an improving move, or **certifies that none exists in that window**. That converts "the basin looks locked" into "the basin *is* locked (within the searched window space)."

**Tool:** `single-flip-check/src/bin/wmaxsat_pilot.rs` (standalone research crate; not committed). For a small window `F` of "free" edges it builds the exact weighted-partial-MaxSAT instance (soft/hard clauses = the monochromatic cliques that pass *through* F), solves it to a proven optimum with **z3** (complete `assert-soft` MaxSAT), splices the result, and confirms with a full clique recount. `|F|` is the compute-scaling knob.

**Validation (opus-reviewed, built subagent-driven TDD):**
- **Paley(17) anchor:** z3's window optimum == exhaustive 2^|F| brute force (136 == 136).
- **f₀ cross-check:** clauses-violated-at-base == an independent monochromatic-clique recount through F (non-circular).
- **Recount-as-truth (the key guarantee):** a reported `delta < 0` is the difference of two independent full `clique_census`/`get_cliques_comprehensive` recounts; z3 only *proposes* an assignment. **False-positive "improvements" are structurally impossible** — the worst the solver can do is *miss* one.

Spec/plan docs: `single-flip-check/docs/2026-06-25-windowed-maxsat-{design,plan}.md` (Phase 1), `…-phase2-{design,plan}.md` (Phase 2), `2026-06-27-windowed-maxsat-phase3-{throughf-design,plan}.md` (Phase 3).

---

## Phase 1 — minimize mono-8 on the best 282-graph (graph 8644, 25,840)

Modes `anchor | window | sweep`. All-soft clauses = potential mono-8 cliques through F; minimizing them minimizes mono-8. Clique-seeded + random windows, sharded, niced.

**Results — EXACT-LOCKED:**
| |F| | windows (exact, proven-optimal) | improved |
|---|---|---|
| 32 | ~600 | 0 |
| 48 | ~101,682 | 0 |

Graph 8644 is **exact-locked up to |F|=48** — each window's proven optimum subsumes *every* single / within-star-pair / full-row / 2-vertex-joint move inside that window. This upgrades the 06-19 verdict ("heuristics couldn't escape") to **"complete solves over ~102k windows found no escape."**

---

## Phase 2 — the 281-base hunt toward zero (Paley(281))

**Goal:** a 281-vertex **monochromatic-8-clique-FREE** graph with **fewer than Paley(281)'s 5,979,680 mono-7-cliques** — a strictly better seed for vertex-extension that could floor *below* the 25,840 wall. (Fewer mono-7s ⇒ fewer latent mono-8s through an added 282nd vertex.)

Modes `base-anchor | base-window | base-sweep`: minimize mono-7 (soft) subject to **hard mono-8 = 0** (forbid creating any mono-8). Feasibility is guaranteed (the base satisfies all hard clauses). Crucially this explores **NON-circulant** mono-8-free moves — the space the 06-19 circulant probe (which found Paley circulant-locally-optimal, 0/140) never tested. `base-anchor` validates the constrained solve vs exhaustive brute force on Paley(17) (mono-4-free, minimize mono-3): z3 == brute (136). A saved hit is recount-confirmed to be mono-8-free *and* lower-mono-7.

**Results — EXACT-LOCKED (all rungs, zero hits):**
| |F| | windows | improved |
|---|---|---|
| 8 | ~59,932 | 0 |
| 16 | ~58,211 | 0 |
| 24 | ~51,348 | 0 |
| 32 | ~55,629 | 0 |
| 48 | ~48,107 | 0 |
| 64 | ~51,704 | 0 |

**Paley(281) is exact-locked to non-circular mono-7-reducing moves across ~325k proven-optimal windows up to |F|=64.** Combined with its circulant-local-optimality (06-19), Paley(281) is robustly locked to local moves of every tested shape and size.

**High-volume fast runs (Phase-3 ~27× speedup):** once the through-F speedup landed, the ladder was re-run at far higher window counts on the fast binary — |F|=32 → 0/392,457, |F|=64 → 0/191,186, plus |F|=48 (locked), all zero escapes. Total across graph 8644 and Paley(281): **~1.0 million+ exact, proven-optimal windows, zero escapes.** A fast **|F|=128** base-sweep (the untested top of the window-size ladder) is in progress as the "bigger local window" long shot. The |F| ladder remains the cheap "leave-no-stone-unturned" grind; a saved hit (a mono-8-free 281-base with fewer mono-7s) would be flagged and recount-confirmed immediately.

---

## Phase 3 — through-F enumeration speedup (~27×)

The per-window cost was dominated by enumerating **all** ~5.98M mono-7-cliques and filtering to the ~25k touching the window. Phase 3 enumerates only the cliques **through** the window directly (common-neighborhood `(k−2)`-clique enumeration), screens the per-window delta from the clause set, and runs the authoritative full recount **only to confirm a candidate hit**.

- **~27× faster per window** (~0.17s vs ~4.6s at |F|=8).
- **opus-validated verdict-equivalent:** the through-F clause multiset is *proven identical* to the slow full-census path (differential test), the screened delta is *exact* (not approximate) under splice-locality + base-hard-free, and **saved hits stay full-recount-confirmed** (the reviewer empirically confirmed the confirm/save branch with an independent recount). The slow path is retained as the oracle / for the anchors.
- **New bottleneck:** z3 subprocess-launch (~0.1–0.15s/call). A batched or in-process MaxSAT solver is the next compute-efficiency lever (a possible Phase 4).

---

## Verdict

Exact (complete-solver) windowed local search — the one genuinely new tool relative to all prior incomplete probes — **confirms, and does not escape, the locks**: ~427k+ proven-optimal windows across graph 8644 (mono-8 minimization) and Paley(281) (mono-7 minimization under mono-8=0), **zero improving moves**, at window sizes up to |F|=64–96. This is a rigorous certification within the sampled window space, not merely heuristic failure.

The implication is the same as the 06-19 "honest framing," now hardened: **bigger local windows will not break these locks.** Reaching 0 (R(8,8) ≥ 283) or a base below the 25,840 floor needs a **structurally-new mono-8-free construction** (generalized Paley / Cayley on non-cyclic groups / algebraic-geometric families — see `search-status` next-steps), not more local search. The validated `wmaxsat_pilot` engine + the `single-flip-check` construction tools are ready to evaluate any candidate base.

---

## Ops note — durable long runs (macOS)

Long local sweeps **must** be launched via `( WMAXSAT_SAVE=… nohup nice -n 5 ./target/release/wmaxsat_pilot base-sweep … > log 2>&1 & )` — the `( … & )` subshell reparents the process to **init/PID 1**, out of the IDE/Claude process tree, so it survives IDE/terminal/Claude restarts (the same reason the Docker workers survive). Plain background launch or `nohup` inside a waiting wrapper does **not** survive — the restart tree-kills the subtree and `nohup` only blocks SIGHUP. macOS has no `setsid`; foreground `sleep` is blocked in the Claude Code environment (use plain `ps`/log checks). Detection of completion (fully detached → no harness notification): count `done:` lines across the per-shard logs (== shard count).
