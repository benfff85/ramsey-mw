# Disk cleanup — 2026-09-09

Mac free space increased from **16.48 GiB to 62.53 GiB**, a measured net recovery of **46.06 GiB**.
Measurements use `df -k` on the host data volume, not Docker's virtual disk capacity. Background
activity and shared/cache file accounting mean individual tool totals should not be added to
estimate the host-space change.

## The pruning job was working

`com.setminusx.ramsey.graph-prune` is loaded and scheduled daily at **04:15 local time**.
Its September 9 run exited successfully and nulled 186,305 non-retained graph edge strings.
It retained 20,142 graphs and reported zero missing base graphs among active/recent stages.

Nulling edge strings frees pages **inside MySQL's tablespace**, not the host filesystem. Before
compaction, MySQL reported 8.86 GiB of allocated data and 16.36 GiB of reusable free pages, while
`graph.ibd` physically occupied 25.477 GiB. The retained edge strings themselves totaled 798,047,889
bytes. No additional edge strings or stage/graph rows were pruned during this cleanup.

## Removed rebuildable artifacts

- Unused Docker build cache older than seven days: `docker buildx prune --builder orbstack
  --filter 'until=168h' --force` reported **6.765 GB** reclaimed. No images, containers, or volumes
  were explicitly pruned.
- Rust **development-profile** artifacts: Cargo reported 9.0 GiB in `ramsey-worker-rust` and
  1.8 GiB in `single-flip-check`. Release outputs, source files, and research results were retained.
- Old Homebrew installer downloads: the scoped cleanup reported 1.7 GB; four additional inspected
  old installer files (two IntelliJ, Unity Hub, and MySQL Workbench) and their cache symlinks were
  removed. Installed applications and Homebrew Cellar versions were not removed.
- Only the `index` subdirectories of IntelliJ 2024.3, 2025.1, 2025.2, and 2025.3 caches were removed.
  Local history, settings, plugins, and other IDE files were left intact.

These cache/artifact deletions are permanent, but their contents can be rebuilt or downloaded again.
The 16 GiB uv cache was in use by a running tool; its cleanup was cancelled without forcing removal.
Ollama models (about 137 GiB), Docker volumes, and saved scientific outputs were left untouched.

## Physical database compaction

Both M1 and M4 fleets were already paused on campaign 10 and remained paused. MySQL, middleware,
queue manager, Redis, and UI were kept running. About **38 GiB** of host free space was available
before the table rebuild. The table has an InnoDB primary key and no FULLTEXT index;
`innodb_file_per_table=1`, `old_alter_table=OFF`.

Created a full `ramsey-dev` logical backup with `mysqldump --single-transaction --quick`, compressed
it, checked its completion marker, and verified it using `gzip -t`. The backup is retained with
mode 0600 at:

`/Users/benferenchak/Ramsey/Database/backups/ramsey-dev-before-compact-20260909.sql.gz`

Backup SHA-256:
`dcfac9ad62b472f17d50775c980496333f20eb6f1c5b200fae5f1ad2c07f474d`

This is a local logical backup, not an off-device disaster-recovery copy. A restore drill was not run.

Ran `SET SESSION lock_wait_timeout=5; OPTIMIZE TABLE graph;` in `ramsey-dev`. MySQL returned
the expected InnoDB recreate/analyze note followed by **status OK**. The file shrank from
27,355,250,688 to 1,178,599,424 bytes, recovering **24.379 GiB** physically.

After refreshing statistics with `information_schema_stats_expiry=0` and `ANALYZE TABLE graph`,
the table reported 1.083 GiB data and 0.001 GiB free. The daily prune was not changed, disabled,
or replaced with automatic compaction. The file will need to grow again as new work accumulates.

This uses MySQL's documented [InnoDB table compaction behavior](https://dev.mysql.com/doc/refman/9.7/en/optimize-table.html)
and accounts for its [temporary-space requirements](https://dev.mysql.com/doc/refman/9.7/en/innodb-online-ddl-space-requirements.html).
The running server was MySQL 9.5.0; the official 9.5 documentation URLs redirected to 9.7.

## Verification

Before and after compaction:

| Check | Before | After |
|---|---:|---:|
| Graph rows | 3,665,898 | 3,665,898 |
| Maximum graph ID | 3,665,903 | 3,665,903 |
| Non-null edge strings | 20,142 | 20,142 |
| Edge-string bytes | 798,047,889 | 798,047,889 |
| `CHECKSUM TABLE graph EXTENDED` | 727,095,979 | 727,095,979 |
| Stage rows | 3,650,581 | 3,650,581 |
| Maximum stage ID | 3,651,122 | 3,651,122 |

- Both active-stage base graphs still contain 39,621 edge characters.
- Zero of the most recent 20,000 stages have missing base-graph edge strings.
- Before compaction, every campaign's stored minimum and all its ties had retained edge data;
  the unchanged graph checksum/counts provide the after-compaction comparison.
- Middleware health: UP, including MySQL and Redis; UI root: HTTP 200.
- No ERROR/Exception lines in the last five minutes of middleware/queue-manager logs at verification.
- Prune LaunchAgent remains loaded at 04:15; last exit code 0.
- M1 and M4 fleet mappings remain campaign 10, PAUSED.
- SHA-256 hashes of the production Rust release worker, the boundary-repair release binary,
  and the saved `results/g276750.txt` research graph are unchanged.

No production code, fleet assignment, container image, or pruning retention policy was changed.
