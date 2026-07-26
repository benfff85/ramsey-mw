#!/bin/bash
# Vast.ai On-start Script for Ramsey Worker Nodes (Ubuntu 22.04 VM)
set -e

# 1. Update and Install Docker/Compose (if not present)
if ! command -v docker &> /dev/null; then
    apt-get update
    apt-get install -y docker.io curl
    # Install Docker Compose V2 (standard for modern Ubuntu)
    apt-get install -y docker-compose-v2
    # Alias to ensure 'docker-compose' command works like your old script
    ln -s /usr/bin/docker-compose /usr/local/bin/docker-compose || true
fi

# Install Loki Docker logging plugin (if not already installed)
docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions 2>/dev/null || true

# 2. Use the persistent workspace directory
mkdir -p /workspace/ramsey
cd /workspace/ramsey

# 3. Create docker-compose file
cat > docker-compose.yml << 'EOF'
services:
  ramsey-worker-rust:
    image: benferenchak/ramsey-worker-rust:develop-amd64
    environment:
      RAMSEY_API_URL: http://www.setminusx.cloud:36000/api/ramsey
      # Fleet abstraction: the target is set in the DB `fleet` table and repointed with
      # PUT /fleets/vast-ai — no redeploy, which matters for burst instances.
      RAMSEY_FLEET: vast-ai
      REDIS_HOST: www.setminusx.cloud
      REDIS_PORT: 36002
      # FLOOR for the work range claimed per cycle, not a fixed size — the worker resizes each
      # cycle from measured throughput. It must stay small enough that a batch fits INSIDE a
      # stage; too large and every batch is abandoned partway when the stage advances.
      WORK_UNIT_FETCH_COUNT: 2000
      WORK_UNIT_PUBLISH_COUNT: 10000
      WORK_UNIT_POLL_FREQ: 5000
      PUBLISH_RESULTS: "false"
      TOP_RESULTS_COUNT: 50    # must match the QM of the campaign this fleet is mapped to
      # Kill switch for the hoisted pair-move evaluation. The hoisted and seeded paths compute
      # identical values, so this only ever trades cost.
      HOIST_ENABLED: "true"
      WORKER_COUNT: 1
    logging:
      driver: loki
      options:
        loki-url: "https://loki.setminusx.cloud/loki/api/v1/push"
        mode: non-blocking
        max-buffer-size: 4m
        loki-retries: 5
        loki-batch-size: 1000
        loki-external-labels: "machine=Vast-AI,service_name={{.Name}}"
    restart: always
EOF

# 4. Detection and Execution
# Detect CPU count (works same as AWS)
CPU_COUNT=$(nproc)
echo "Detected $CPU_COUNT CPU cores. Launching workers..."

# Pull and Scale
docker compose pull
docker compose up -d --scale ramsey-worker-rust=$CPU_COUNT

echo "Ramsey workers active: $(date)" >> /workspace/startup.log