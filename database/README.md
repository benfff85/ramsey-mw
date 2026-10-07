# Ramsey Database Setup

## Inject Password to Configs

```bash
export MYSQL_PASSWORD="<password>" 
sed "s/<password>/$mysql_pass/g" init-script.ddl > init-script-sensitive.ddl
```

## Create the MySQL Instance

Create the MySQL instance  with docker by deploying the compose defined in `ramsey-db-compose.yml`

```bash
docker compose -f ramsey-db-compose.yml -p ramsey-db up -d
```

## Initialize DDL

Create the databases, users, and tables.

```bash
mysql -u root -p -h 127.0.0.1 -P 3306 < init-script-sensitive.ddl
```

## Initialize Data

For development this will insert a sample record into the `graph` table.

```bash
mysql -u root -p -h 127.0.0.1 -P 3306 < init-data.sql
```

## Applying Schema Changes to a Running Instance

There is no migration tool — `init-script.ddl` is the schema of record for **fresh** installs only, so any
change to it must also be applied by hand to live instances. Use online DDL so the search keeps running:

```sql
ALTER TABLE `ramsey-dev`.stage ADD INDEX idx_stage_status (status), ALGORITHM=INPLACE, LOCK=NONE;
```

Applied so far (keep this list in sync with `init-script.ddl`):

| Date | Change | Why |
|---|---|---|
| 2026-07-28 | `stage`: `KEY idx_stage_status (status)` | The QM's cross-campaign ACTIVE-stage query passes `campaignId = null`, which cannot use `idx_stage_campaign_status`. Was a 45 ms full scan of 215k rows on every progression tick and twice per stage advance. See `../docs/investigations/post-hoist-bottleneck-review.md`. |
| 2026-10-07 | `graph`: `parent_graph_id`, `flipped_edges`, `graph_hash`, `lineage_depth` (ALGORITHM=INSTANT, 0.07 s live) | Delta lineage: ordinary graphs store parent + flips + hash instead of 40 KB of edge data. See `../../docs/superpowers/plans/2026-10-07-graph-delta-lineage.md`. |

## Pruning `graph.edge_data`

`graph` grows without bound: every stage advance writes a new 39,621-character `edge_data` bitstring,
~2–4 GB/day at current stage cadence. Nothing reads a superseded stage's bitstring — the search
trajectory the UI plots is `graph.clique_count`, which is tiny and must be kept. So the prune **NULLs
`edge_data`** rather than deleting rows: deleting would destroy `clique_count` and break
`StageRepo.findProgressionByCampaignId`, which inner-joins `stage` to `graph`.

Retention set (build as a temp table, then null everything outside it):

- base graphs of the last **20,000** stages — 4× the `CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT` reseed window
- every perturbation kick seed (`stage.details LIKE '%PERTURBATION kick%'`)
- every ACTIVE stage's base graph
- each campaign's minimum-`clique_count` graph (the incumbents)
- every lineage snapshot (`lineage_depth = 0`): delta rows are rebuilt from these

```sql
CREATE TABLE keep_graphs (graph_id INT PRIMARY KEY);
-- ... populate from the four rules above ...

-- HIGH-WATER-MARK GUARD — see below. Without it this corrupts the live chain.
SET @hwm = (SELECT MAX(graph_id) - 1000 FROM graph);

UPDATE graph SET edge_data = NULL
WHERE edge_data IS NOT NULL AND graph_id < @hwm
  AND graph_id NOT IN (SELECT graph_id FROM keep_graphs)
LIMIT 50000;  -- repeat until 0 rows affected
```

**The guard is not optional.** The retention set is a snapshot, but stages advance every ~3 s while the
batched UPDATE runs. On 2026-07-30 the prune ran without it and nulled 16 graphs that were created
*after* the snapshot — including the then-active stage's base — which threw
`NullPointerException: ... because "edgeData" is null` from `GraphHashUtil.computeDerivedGraphHash`
on every QM progression tick until the chain advanced past them. Recovery was possible only because
Redis `stage_config:{stageId}` embeds the active graph's bitstring. Excluding the newest ~1,000
graph IDs costs ~40 MB and removes the race entirely.

`reseedProcessedGraphHashes` already skips null `edge_data`, so a nulled graph outside the active
chain degrades cycle prevention by one entry rather than failing.

### What it reclaims

Nulling frees pages *inside* the tablespace; it does not shrink `graph.ibd`. After the 2026-07-30
prune the file stayed at 21.55 GB but `data_free` went to 18.38 GB, so inserts reuse that space and
the file stops growing for ~5–9 days. Run `ANALYZE TABLE graph` afterwards — `data_free` reads 0
until statistics refresh. Reclaiming actual disk needs a table rebuild such as `OPTIMIZE TABLE graph`.
Budget temporary space for both the rebuilt table and any sort files; do not start this on a nearly
full disk. For fresh size measurements, set `SESSION information_schema_stats_expiry=0` before
querying `information_schema.tables` (otherwise cached values can still describe the old file).

On **2026-09-09**, after cache cleanup and a verified logical backup, a one-off compaction reduced
`graph.ibd` from **25.477 GiB to 1.098 GiB**. All 3,665,898 graph rows and 20,142 retained edge strings
survived, and the full-table checksum was unchanged. The daily prune remains unchanged; compaction
is **not** scheduled daily. See [the cleanup record](../docs/investigations/disk-cleanup-2026-09-09.md)
for the backup, checks, and disk-space measurements. The file can grow again as workers generate
new graphs between daily prunes; compaction is not a permanent cap on its size.

### Scheduled daily (2026-08-24)

`prune-graph-edge-data.sh` implements the procedure above and runs **daily at 04:15** via the
LaunchAgent in `com.setminusx.ramsey.graph-prune.plist`. Install instructions are in the plist's own
comment; the log is `~/Library/Logs/ramsey-graph-prune.log`.

It is safe to run at any time and as often as you like — with nothing outside the retention set it
nulls 0 rows and exits. Run `./database/prune-graph-edge-data.sh --dry-run` to see what a run would
do without touching anything.

Daily rather than weekly because the retention window is now *shorter than a day*: at ~21 stages/min
the fleet produces ~30,000 stages a day against a 20,000-stage retention window. A weekly cadence
would let ~200,000 prunable rows accumulate between runs, which is what let `data_free` hit zero.

Beyond the guard, the script adds three things the manual procedure did not have:

- **A single-run lock** (`mkdir`-based; macOS ships no `flock`). Two overlapping prunes would each
  rebuild the retention set and recompute the high-water mark, destroying the guarantee the fixed
  mark exists to provide. A lock older than six hours is treated as abandoned.
- **An explicit `PATH`.** launchd starts jobs without `/usr/local/bin`, where the `docker` CLI
  lives, so without this the scheduled run fails while the interactive run succeeds.
- **A post-run check** that no ACTIVE stage, and none of the last `KEEP_STAGES` stages, has a NULL
  base graph. The 2026-07-30 failure was silent until the queue manager started throwing; this
  turns it into a non-zero exit on the same run that caused it.

Verified on install by running it through launchd itself (`launchctl kickstart`), not just from a
shell — that is where `PATH` and Docker-socket access break.

Re-run manually any time, or when `data_free` approaches zero.

### Run log

| date | before | after | rows nulled | notes |
|---|---|---|---|---|
| 2026-07-30 | 21.55 GB | 21.55 GB file, `data_free` 18.38 GB | — | ran **without** the high-water-mark guard; nulled 16 post-snapshot graphs including the active base, breaking QM progression until the chain advanced past them. This is why the guard exists. |
| 2026-08-24 | 25.21 GB, `data_free` **0.00 GB** | 4.94 GB, `data_free` 20.20 GB | 529,142 | clean. 12 batches of 50k. Retention set 20,116; 20,136 rows left holding data. Verified after: active stage's base intact, 0 of the last 20,000 stages have a null base, 0 QM errors, stage rate unchanged at ~19–21/min. |

Two things learned on the 2026-08-24 run, worth doing the same way next time:

- **Compute the high-water mark once and hold it for the whole run**, rather than recomputing per
  batch. A fixed mark guarantees that every graph created *during* the prune sits above it and is
  protected for the entire run; a moving mark only protects the newest 1,000 at each instant, which
  is a weaker guarantee the longer the run takes.
- **`data_free` had reached exactly 0.00 GB**, meaning the tablespace had stopped absorbing inserts
  and the file was growing on disk again. That is the real trigger, and it arrived ~25 days after
  the previous prune rather than the ~5–9 days the 2026-07-30 note predicted — the estimate was made
  before the throughput work, and stage cadence has roughly doubled since. Check `data_free`
  directly (after `ANALYZE TABLE`, which is required or it reads 0 spuriously) rather than trusting
  a day count.

## Set up the MySQL Connection

Login with DBeaver, you may need to set the following in the Driver properties tab of the connection details.

1. `allowPublicKeyRetrieval`: True
2. `useSSL`: False

## Rolling the middleware back past graph lineage (fallback L2)

The pre-lineage middleware returns NULL `edgeData` for delta rows and ignores lineage fields on
POST. So: (1) set the QM's `GRAPH_STORAGE_MODE` to `OFF` and recreate it; (2) wait for one stage
advance; (3) run `database/materialize-graphs.sh`. It refuses until (1) and (2) hold, then writes
full edge data onto every ACTIVE base and each active campaign's last 5,000 bases. (4) Only then
retag `ramsey-mw:rollback-pre-delta-lineage` to `:develop` and recreate the mw.

## Restoring from a backup (last resort)

Backups: `~/Ramsey/Backups/<date>-<label>/` — `mysql.sql.zst`, `mysql-checksums.txt`, `hwm.txt`,
`dragonfly-data/`, `redis-pre.json`, `images.txt`, `git-heads.txt`.

1. Pause every fleet (`POST /api/ramsey/fleets/<platform>/pause`) and stop the QM:
   `docker stop ramsey-ramsey-queue-manager-1`.
2. Roll images back to the backup's `images.txt` (`docker tag <id> benferenchak/<repo>:develop`).
3. MySQL: `docker exec ramsey-db-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE \`ramsey-dev\`"'`,
   then `zstd -dc mysql.sql.zst | docker exec -i ramsey-db-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'`
   (the dump contains CREATE DATABASE). Re-create grants for `ramsey-user-dev` if missing. Verify
   with the queries in `backup-mysql.sh` against `mysql-checksums.txt`.
4. Redis: `docker stop ramsey-redis-1`; replace the volume contents:
   `docker run --rm -v ramsey_dragonfly-data:/data -v <backup>/dragonfly-data:/src alpine sh -c 'rm -rf /data/* && cp -a /src/. /data/'`;
   `docker start ramsey-redis-1`; run `compare_fingerprints.py redis-pre.json <fresh>` → LOSSLESS.
5. Recreate mw then QM via compose; the QM's `ensureActiveStageInitialized` re-seeds any missing
   stage config. Resume fleets (check `campaignId` first). Stages created after the backup are gone;
   the search continues from the restored ACTIVE stage.
