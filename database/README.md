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

## Set up the MySQL Connection

Login with DBeaver, you may need to set the following in the Driver properties tab of the connection details.

1. `allowPublicKeyRetrieval`: True
2. `useSSL`: False
