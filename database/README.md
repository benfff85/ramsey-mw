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
until statistics refresh. Reclaiming actual disk needs `OPTIMIZE TABLE graph`, which rebuilds the
table and temporarily needs ~2× its size; not run, and not needed while free space remains.

Re-run the prune weekly, or when `data_free` approaches zero.

## Set up the MySQL Connection

Login with DBeaver, you may need to set the following in the Driver properties tab of the connection details.

1. `allowPublicKeyRetrieval`: True
2. `useSSL`: False
