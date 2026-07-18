# Fleet Abstraction — Implementation Plan (Phase 1)

**Date:** 2026-07-18
**Author:** Ben Ferenchak + Claude
**Status:** PLANNED — not yet implemented. This is the spec of record; a future session should be able to implement Phase 1 from this doc alone.
**Two-phase context:** Phase 1 = the **fleet abstraction** (this doc). Phase 2 = **perturbation / Iterated Local Search** (a separate doc), which builds on the fleet targeting this delivers. Phase 2 is out of scope here except the forward-pointer in §11.

**Decisions locked by Ben (do not re-litigate):**
1. **Remove the concept of `campaign.status`** entirely (targeting no longer keys off it).
2. **Single, campaign-agnostic queue manager** — retire the two-QM (`ramsey-queue-manager` + `ramsey-queue-manager-mseed`) setup.
3. **Fleets are per host for now, but identified by *platform*** — values `m4-max`, `m1`, `vast-ai` (not host names).
4. Fleet→campaign mapping lives in the DB; workers are configured with a stable platform id; **repointing a fleet is a DB update, never a redeploy.**

---

## 1. Motivation — the problem this removes

Today, "which workers work which campaign" is encoded in **`RAMSEY_CAMPAIGN_ID` env vars** on the worker services *and* the queue-manager services. Every reassignment therefore requires a compose edit + Portainer redeploy + container recreation + verification. This session alone did that ~6 times (deep-wall re-runs, rotations, the campaign-3 benchmark). Concrete pain points:

- **The M1 can't be repointed at all without SSH** — it's a separate machine; when it was left pinned to an inactivated campaign it sat idle and there was no way to move it from here.
- **Every multi-seed rotation** was: edit `docker/main/ramsey-compose.yml` → PUT Portainer stack 7 → wait ~60s for recreation → verify. Error-prone (stale env, recreation blips) and slow.
- **The two-QM setup** (`ramsey-queue-manager` pinned to campaign 10, `ramsey-queue-manager-mseed` pinned to the rotating campaign) exists *only* because the QM is also env-pinned. Rotations require flipping the mseed QM's env too.
- **Phase 2 (ILS) needs automated retargeting** — a controller must be able to move a fleet to a freshly-created campaign/stage without a human running a deploy.

**After Phase 1:** "who works what" is pure DB state. Repointing any fleet — including the M1, including from an automated controller — is a single `UPDATE`/API call, and workers follow on their next poll (seconds). No redeploy, no SSH, no stale env.

---

## 2. Target model

### Concepts

| Concept | Before | After |
|---|---|---|
| **campaign** | Has `status` (ACTIVE/INACTIVE); status is how the system knows a campaign is "live". A logical lineage of stages from one seed. | **No `status`.** Purely a lineage/grouping of stages for history/reporting/best-tracking. "Live" is *derived*: a campaign is being worked iff a fleet points at it and it has an ACTIVE stage. |
| **stage** | Has `status`; exactly one ACTIVE stage per live campaign = the current base graph being swept. | **Unchanged.** Still the marker of "current base". Retained — the worker resolves "my campaign's current stage" via `stage.status=ACTIVE`, and the QM's progression logic already uses it. |
| **fleet** *(new)* | — (implicit: a set of workers sharing a `RAMSEY_CAMPAIGN_ID`) | A first-class row: `platform → campaign_id`. The single source of truth for targeting. |
| **worker** | Pinned to a campaign via `RAMSEY_CAMPAIGN_ID`. | Pinned to a **platform** via `RAMSEY_FLEET` (stable, set once). Resolves work via the fleet mapping. |
| **queue manager** | Two services, each pinned to one campaign via `RAMSEY_CAMPAIGN_ID`. | **One** service, no campaign env. Manages progression for **every campaign that has an ACTIVE stage**. |

### Why `stage.status` stays but `campaign.status` goes
Ben's directive is specifically to remove **campaign** status. `stage.status` still does real work (marks the one current base per campaign; drives the QM's exhaustion/progression loop). Removing it too would be the "fleet→stage pointer" variant (§12, deferred) — a bigger refactor not asked for. So: **keep `stage.status`, drop `campaign.status`.** All the state `campaign.status` used to carry is derivable:
- "Is campaign C live / being worked?" → `EXISTS(fleet WHERE campaign_id=C)` AND `EXISTS(stage WHERE campaign_id=C AND status='ACTIVE')`.
- "Is C banked/historical?" → no fleet points at it (and/or it has no active stage).

### End-state runtime picture
```
fleet table:          worker (env RAMSEY_FLEET=m4-max)
  m4-max → 3            └─ GET /fleets/m4-max/active-stage ─┐
  m1     → 10          worker (env RAMSEY_FLEET=m1)          │  mw resolves
  vast-ai→ (null)       └─ GET /fleets/m1/active-stage ──────┤  fleet→campaign_id
                                                             │  →stage WHERE status=ACTIVE
single QM (no env): each tick, for every campaign with an ACTIVE stage,
  run init + progression (stage-scoped Redis keys, exactly as today, just looped).
```
Repoint = `UPDATE fleet SET campaign_id=? WHERE platform=?` (or the PUT endpoint). Workers follow next poll.

---

## 3. Data model & schema

### New `fleet` table
```sql
CREATE TABLE fleet (
  platform      VARCHAR(32)  NOT NULL PRIMARY KEY,   -- 'm4-max' | 'm1' | 'vast-ai' (extensible)
  campaign_id   INT          NULL,                   -- NULL = idle (workers poll & wait)
  updated_date  DATETIME(6)  NULL,
  note          VARCHAR(255) NULL,                   -- free text, e.g. 'benchmark' / 'ILS run 3'
  CONSTRAINT fk_fleet_campaign FOREIGN KEY (campaign_id) REFERENCES campaign(campaign_id)
);
```
Seed with current reality at migration time (see §7 / §13 for the values live on the day of rollout).

### `campaign.status` removal
- `campaign` currently has a `status ENUM('ACTIVE','INACTIVE')`. **Drop it** — but only in the *last* migration step (§7 Phase 1d), after all code references are gone.
- Known code references to audit and remove (from a 2026-07-18 grep; verify at implementation time):
  - `entity/Campaign.java` — the `Status` enum + field.
  - `controller/CampaignController.java` — `@RequestParam status` on `GET /campaigns`, passed to `campaignService.getCampaigns(...)`.
  - `service/CampaignService.java` / `repository/CampaignRepo.java` — any `findByStatus` / status-filtered query.
  - Reseed/rotation SQL and any admin scripts that `UPDATE campaign SET status=...`.
  - **`ramsey-ui`** — see §6d (the dashboard's "which campaigns are active" logic must switch off `campaign.status`).

> **Do NOT drop the column while `Campaign.java` still maps it** — Hibernate will fail on startup. Order: remove code refs → deploy → then `ALTER TABLE campaign DROP COLUMN status`.

---

## 4. API contracts (middleware)

All under the existing `/api/ramsey` base.

### 4.1 Resolve a fleet's current stage (the worker's hot call)
```
GET /api/ramsey/fleets/{platform}/active-stage
 200 → StageDto  { stageId, campaignId, baseGraphId, workEnumerationStrategy, ... }   (same shape workers get today)
 204 → (no body)  fleet unmapped (campaign_id NULL) OR mapped campaign has no ACTIVE stage → worker idles/polls
 404 → unknown platform
```
Resolution: `fleet.platform → campaign_id → SELECT stage WHERE campaign_id=? AND status='ACTIVE' LIMIT 1`. Return the same StageDto the worker already consumes so nothing downstream changes.

### 4.2 Repoint / read the mapping (ops + Phase-2 controller)
```
GET  /api/ramsey/fleets                       → [ {platform, campaignId, updatedDate, note}, ... ]
GET  /api/ramsey/fleets/{platform}            → {platform, campaignId, updatedDate, note}
PUT  /api/ramsey/fleets/{platform}            body {campaignId: <int|null>, note?: <str>}  → 200 updated row
```
`PUT` upserts the mapping and sets `updated_date=now`. `campaignId=null` idles the fleet. This is the single "move a fleet" primitive used by both a human (curl) and the Phase-2 ILS controller.

### 4.3 Bank / retire a campaign (replaces "set campaign INACTIVE")
Banking is now two independent actions, both stage/fleet-level (no campaign.status):
- **Stop working it:** `PUT /fleets/{platform}` with a different campaign (or null). Workers leave.
- **Make the QM stop managing it:** deactivate its stage. Add:
  ```
  POST /api/ramsey/campaigns/{id}/deactivate   → marks the campaign's ACTIVE stage INACTIVE (no new stage)
  ```
  After this the campaign has no ACTIVE stage → the single QM ignores it → it's historical. (Its graphs/stages remain for reporting.)

> Auth: mw currently has no auth (internal network). Keep these internal; add auth later if mw is ever exposed. Note in the endpoint comments.

---

## 5. Component change — Worker (`ramsey-worker-rust`)

Minimal and backward-compatible.

- **Config (`main.rs`):** read `RAMSEY_FLEET` (e.g. `m4-max`). **Fallback:** if `RAMSEY_FLEET` is unset, keep today's `RAMSEY_CAMPAIGN_ID` path (so rollout is machine-by-machine and reversible). Log which mode is active at startup.
- **Client (`client.rs`):** add `get_fleet_active_stage(platform) -> Option<Stage>` calling `GET /fleets/{platform}/active-stage` (200 → Some(stage), 204 → None). Keep `get_stages_by_campaign` for the fallback path.
- **Cycle (`worker.rs::get_or_fetch_stage_id` / `cycle`):** in fleet mode, **re-resolve the fleet's current stage each cycle** (it's one cheap GET) rather than caching `campaign_id` forever. If the returned `stageId` differs from the cached one → the fleet was repointed (or the stage progressed) → `clear_stage_cache()` and load the new stage. The existing "stage config missing → refresh" and "all work claimed → clear cache" paths already handle the mechanics of a stage change; this just adds "the stage can also change because the *fleet* moved."
  - If `active-stage` returns 204 (fleet idle / no active stage) → sleep `poll_interval` and retry (same as today's "no active stage" behavior).
- **Transition safety:** a worker mid-batch when the fleet is repointed finishes its claimed range against the *old* stage (valid work for that stage), then next cycle re-resolves and moves. No coordination needed. A late result submitted to the old stage is still correct for that stage.
- **Tests:** unit — fleet-mode resolution (200/204/404 handling), fallback-mode unchanged; the repoint transition (cached stage != resolved stage → cache cleared).

---

## 6. Component change — Middleware (`ramsey-mw`)

### 6a. New fleet slice
- `entity/Fleet.java` (`platform` PK, `campaignId`, `updatedDate`, `note`).
- `repository/FleetRepo.java`.
- `service/FleetService.java` — `getActiveStage(platform)` (the resolve logic), `list()`, `get(platform)`, `upsert(platform, campaignId, note)`.
- `controller/FleetController.java` — the endpoints in §4.
- `dto/FleetDto.java`.

### 6b. Remove `campaign.status`
Per §3. Delete the `Status` enum/field from `Campaign.java`, drop the `status` filter from `CampaignController`/`CampaignService`, remove any `CampaignRepo.findByStatus*`. Keep `Campaign` otherwise intact (it's still the lineage entity). Column dropped in the final migration step.

### 6c. Add campaign deactivate endpoint (§4.3) — small addition to `CampaignController`/`StageService` to flip the campaign's ACTIVE stage to INACTIVE.

### 6d. UI dependency — **must be handled or the dashboard breaks**
`ramsey-ui` resolves "active campaigns" for its live view. Audit `ramsey-ui-rest` (`sampler/ActiveStageResolver`, `web/DashboardController`, `client/MwClient`) and the React `App.tsx` campaign selection for any reliance on `campaign.status`. Switch the notion of "active campaign" to **"has an ACTIVE stage"** (or "is targeted by a fleet"). Options:
- Minimal: the BFF derives active campaigns from "campaigns with an ACTIVE stage" (a query that doesn't need `campaign.status`).
- Nicer (later): surface the `fleet` table in the UI ("m4-max → campaign 3") — a natural dashboard addition, not required for v1.
This is the **highest-risk cross-cutting item** — the multi-campaign live view (ramsey-ui PR #16) currently keys off active campaigns; do not drop `campaign.status` until the UI no longer needs it. Sequence the UI change in the same window as Phase 1d.

---

## 7. Component change — Queue Manager (`ramsey-queue-manager`)

Make it **single and campaign-agnostic.**

- **Remove `RAMSEY_CAMPAIGN_ID`** dependency (`RamseyConfig.campaignId`, and the compose env).
- **Generalize the two `@Scheduled` loops** (`ensureActiveStageInitialized`, `StageProgressionMonitor`) from "my one campaign" to "**every campaign that has an ACTIVE stage**": fetch all ACTIVE stages (one query across campaigns), then run the *existing* per-stage init + progression logic for each. All QM state is already **stage-scoped** in Redis (`stage_config:{stageId}`, `stage_work_index:{stageId}`, `processed_count:{stageId}`, `best_results:{stageId}`) and `processed_graph_hashes` is a shared add-only set (SHA-256 = exact-graph identity, safe cross-campaign) — so managing N campaigns concurrently is a loop, not a redesign.
- **Retire `ramsey-queue-manager-mseed`** (remove the service). One `ramsey-queue-manager`, `scale: 1`, no campaign env.
- **Load / concurrency:** the QM tick is light (counter checks + occasional progression). Managing a handful of live campaigns (≤ number of fleets, realistically ≤ 3–4) in one loop is trivial. Note it so nobody worries.
- **Dormant campaigns:** a campaign with an ACTIVE stage but **no fleet** (no workers feeding it) will not progress — exhaustion is claim-based (`stage_work_index >= totalPairs` never reached without workers), so the QM just leaves it. Harmless. To truly stop the QM from considering it, deactivate its stage (§4.3).
- **Tests:** unit — the multi-campaign iteration progresses each independent active stage correctly; a campaign with no active stage is skipped; the existing single-campaign behavior is preserved when only one campaign is active.

---

## 8. Compose / deploy template changes

The env swap is the **last** compose edit each service ever needs; after it, targeting is DB-only.

- `docker/main/ramsey-compose.yml` (M4 Max): worker service `RAMSEY_CAMPAIGN_ID` → `RAMSEY_FLEET: m4-max`. Remove `ramsey-queue-manager-mseed`; strip `RAMSEY_CAMPAIGN_ID` from `ramsey-queue-manager`.
- `docker/m1-worker/ramsey-compose.yml` (M1): `RAMSEY_CAMPAIGN_ID` → `RAMSEY_FLEET: m1`. **This is the final manual M1 deploy** — after it, the M1 repoints via DB (`UPDATE fleet SET campaign_id=? WHERE platform='m1'`), solving the SSH problem permanently.
- `docker/vast-ai/on-start.sh`: the generated worker compose sets `RAMSEY_CAMPAIGN_ID: 10` → `RAMSEY_FLEET: vast-ai`. New Vast instances then just need `fleet[vast-ai]` pointed somewhere.

---

## 9. Rollout — phased, reversible, live-system-safe

The system is live and must not drop work. Each phase is independently deployable and reversible.

**Phase 1a — schema + mw fleet endpoints (additive, nothing changes behaviorally).**
- Add `fleet` table; seed with current reality (§13).
- Ship `FleetController`/`FleetService` (resolve + PUT). `campaign.status` still present; workers still use `RAMSEY_CAMPAIGN_ID`.
- Deploy mw. Verify `GET /fleets` and `GET /fleets/{p}/active-stage` return correctly. **Rollback:** revert mw; drop table. Zero worker impact.

**Phase 1b — worker fleet-awareness (backward compatible).**
- Worker supports `RAMSEY_FLEET` with `RAMSEY_CAMPAIGN_ID` fallback. Build image.
- Recreate M4 workers with `RAMSEY_FLEET=m4-max` (established local-build + `--force-recreate` pattern, at a stage boundary). Verify they resolve via the fleet endpoint and process the mapped campaign.
- Repeat on M1 (the last manual M1 deploy) and update the Vast `on-start.sh`.
- **Rollback:** unset `RAMSEY_FLEET` → workers fall back to `RAMSEY_CAMPAIGN_ID`. Fully reversible.
- **Validate the payoff here:** `UPDATE fleet SET campaign_id=<other> WHERE platform='m4-max'` and confirm the fleet follows within one poll **with no redeploy**. This is the core proof.

**Phase 1c — single campaign-agnostic QM.**
- Deploy the generalized QM (no env). Retire `ramsey-queue-manager-mseed`.
- Verify one QM progresses all live campaigns; check no double-management (only one QM now).
- **Rollback:** redeploy the two pinned QMs from git.

**Phase 1d — remove `campaign.status` (with the UI change).**
- Land the `ramsey-ui` change (active = "has active stage" / fleet-derived) **first or together**.
- Remove `campaign.status` code refs in mw; deploy; then `ALTER TABLE campaign DROP COLUMN status`.
- **Rollback:** the column drop is the only irreversible step — do it last, after the code has run statusless in prod for a bit. Re-add column + code from git if needed (data is derivable).

---

## 10. Edge cases & risks

1. **Fleet repointed mid-batch** — worker finishes old claimed range (valid), moves next cycle. Safe.
2. **Two fleets on one campaign** (e.g., M4 + M1 both on campaign X) — both resolve to the same ACTIVE stage, claim from the same Redis counter → additive throughput. Explicitly supported (it's what we do manually today).
3. **Fleet points at a campaign with no ACTIVE stage** (just deactivated) → 204 → worker idles. Resumes when repointed or a stage appears.
4. **QM manages a fleet-less campaign** — sits (claim-based exhaustion never fires without workers). Harmless; deactivate the stage to fully retire.
5. **`processed_graph_hashes` global set** — unchanged; SHA-256 exact-graph identity, safe across concurrently-managed campaigns.
6. **Dropping `campaign.status` breaks the UI** — mitigated by §6d; gate the column drop on the UI change.
7. **Stale worker cache on repoint** — mitigated by re-resolving the fleet each cycle (§5). Do NOT cache `campaign_id` for the process lifetime.
8. **Two QMs briefly overlapping during 1c cutover** — deploy the single QM and remove mseed in one Portainer apply; brief overlap is safe (QM ops are idempotent/claim-based) but minimize it.
9. **Hibernate startup failure** if the column is dropped before the entity stops mapping it — enforced ordering in 1d.

---

## 11. Phase 2 hook (perturbation / ILS) — forward pointer only

Phase 2 adds an **ILS controller** (likely an external process, to keep the production QM clean) that, on a stage-denominated wall, kicks the incumbent-best graph by a tuned magnitude and re-descends. With Phase 1 in place, its retargeting is trivial and deploy-free:
- Create a new campaign (or new stage) with the kicked seed via existing insert paths.
- `PUT /fleets/{platform}` to point the fleet at it. The fleet follows automatically.
Per-kick **new campaigns** give clean per-basin history; the fleet abstraction makes that as cheap as a stage insert. **Prerequisite experiment (before building Phase 2):** the "what kick size *k* escapes the 25,840 basin" probe on graph 8644 — see the Phase-2 doc when written. Note the |F|≤128 MaxSAT locks are *sampled, not exhaustive* (see `windowed-maxsat-investigation.md`), so the unsampled multi-edge space ILS explores is not closed by proof.

---

## 12. Deferred alternative — fleet→stage (full status removal)

A cleaner end-state points the fleet directly at a `stage_id` and has the QM update the pointer on progression, removing **both** `campaign.status` *and* `stage.status`. Not chosen for Phase 1 because (a) Ben asked only to remove *campaign* status, (b) it couples the QM to fleet-pointer writes and multi-fleet-per-campaign fan-out. Revisit as a v2 simplification if the stage-status marker ever becomes friction.

---

## 13. Current-state snapshot (fill in at implementation time)

As of 2026-07-18 the live targeting is: **M4-Max 14 workers → campaign 3** (benchmark), **M1 8 workers → campaign 16** (inactive → idle; this is exactly the "can't repoint without SSH" pain this plan fixes), **Vast-ai → none**. Campaign 10 is ACTIVE with no workers. Seed the `fleet` table to match whatever is live on rollout day:
```sql
INSERT INTO fleet (platform, campaign_id, updated_date, note) VALUES
  ('m4-max', <live M4 campaign>, NOW(6), 'phase1a seed'),
  ('m1',     <live M1 campaign>, NOW(6), 'phase1a seed'),
  ('vast-ai', NULL,              NOW(6), 'idle');
```

---

## 14. Definition of done (Phase 1)

- [ ] `fleet` table live; `GET/PUT /fleets` + `GET /fleets/{p}/active-stage` working.
- [ ] All workers (M4, M1, Vast template) run on `RAMSEY_FLEET`; **a fleet repoint via DB/API moves them with no redeploy** (proven live, incl. the M1 without SSH).
- [ ] One campaign-agnostic QM manages all live campaigns; `ramsey-queue-manager-mseed` retired.
- [ ] `campaign.status` gone from code and schema; UI's "active campaign" derives from active-stage/fleet.
- [ ] Docs updated (this file → status IMPLEMENTED; README index; memory `project` note). Rotations/repoints henceforth are DB updates.
