# Grafana + Loki Centralized Logging

Centralized log aggregation for Docker containers across multiple hosts using Loki and Grafana.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐
│   MacBook #1    │     │   MacBook #2    │
│  (Log Server)   │     │   (Remote)      │
├─────────────────┤     ├─────────────────┤
│ Docker + Loki   │◄────│ Docker + Loki   │
│ Docker Driver   │     │ Driver          │
│                 │     │                 │
│ ┌─────────────┐ │     │ ┌─────────────┐ │
│ │   Grafana   │ │     │ │ containers  │ │
│ │    Loki     │ │     │ └─────────────┘ │
│ └─────────────┘ │     └─────────────────┘
└─────────────────┘
```

## Quick Start

### 1. Start the Logging Stack (on the "log server" MacBook)

```bash
cd grafana
docker compose up -d
```

This will create containers named `grafana-loki-1` and `grafana-grafana-1`.

Access Grafana at `http://localhost:35000`
- Username: `admin`
- Password: `admin`

### 2. Install the Loki Docker Driver (on BOTH MacBooks)

```bash
docker plugin install grafana/loki-docker-driver:3.6.3-amd64 --alias loki --grant-all-permissions
```

> **Note**: For Apple Silicon (M1/M2/M3), use:
> ```bash
> docker plugin install grafana/loki-docker-driver:3.6.3-arm64 --alias loki --grant-all-permissions
> ```

### 3. Configure Docker to Ship Logs to Loki

#### Option A: Per-Container (Recommended for Testing)

Add to your container's logging config in docker-compose.yml:

```yaml
services:
  your-service:
    logging:
      driver: loki
      options:
        loki-url: "http://<LOG_SERVER_IP>:35001/loki/api/v1/push"
        loki-retries: "5"
        loki-batch-size: "400"
```

Replace `<LOG_SERVER_IP>` with the IP of the MacBook running Loki.

#### Option B: Global Docker Daemon Config

For OrbStack, create/edit `~/.orbstack/config/docker.json`:

```json
{
  "log-driver": "loki",
  "log-opts": {
    "loki-url": "http://<LOG_SERVER_IP>:35001/loki/api/v1/push",
    "loki-retries": "5",
    "loki-batch-size": "400"
  }
}
```

Then restart OrbStack:
```bash
orb restart
```

> For standard Docker Desktop, the file is `~/.docker/daemon.json`.

### 4. Add Labels for Better Filtering (Optional)

Add labels to your containers for easier log filtering in Grafana:

```yaml
services:
  ramsey-worker-rust:
    labels:
      - "host=macbook-1"  # or macbook-2
      - "project=ramsey"
    logging:
      driver: loki
      options:
        loki-url: "http://<LOG_SERVER_IP>:35001/loki/api/v1/push"
```

## Querying Logs in Grafana

1. Open Grafana at `http://localhost:35000`
2. Go to **Explore** (compass icon)
3. Select **Loki** as the data source

### Example Queries

```logql
# All logs from a specific container
{container_name="ramsey-worker-rust"}

# Logs containing "error" (case-insensitive)
{container_name=~"ramsey.*"} |~ "(?i)error"

# Logs from a specific host (if labeled)
{host="macbook-1"}

# Filter by compose project
{compose_project="ramsey"}

# Parse JSON logs and filter by level
{container_name="ramsey-mw"} | json | level="ERROR"
```

## Ports

| Service | Port | Description |
|---------|------|-------------|
| Grafana | 35000 | Web UI for log visualization |
| Loki    | 35001 | Log ingestion API |

## Troubleshooting

### Check if Loki Plugin is Installed
```bash
docker plugin ls
```

### Test Loki Connection
```bash
curl -s "http://localhost:35001/ready"
# Should return: ready
```

### View Plugin Logs (if logs aren't appearing)
```bash
# Check if containers are using the loki driver
docker inspect <container_id> --format '{{.HostConfig.LogConfig.Type}}'
```

### Firewall / Network Issues
Make sure port 35001 is accessible between machines:
```bash
# From the remote MacBook
curl -s "http://<LOG_SERVER_IP>:35001/ready"
```

## Data Retention

By default, Loki retains logs indefinitely. To add retention, modify `loki-config.yml`:

```yaml
limits_config:
  retention_period: 7d  # Keep logs for 7 days
```

## Stopping the Stack

```bash
docker compose down

# To also remove stored data:
docker compose down -v
```
