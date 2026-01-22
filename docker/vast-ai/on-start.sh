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
      RAMSEY_API_URL: http://www.setminusx.com:36000/api/ramsey
      RAMSEY_CAMPAIGN_ID: 1
      REDIS_HOST: www.setminusx.com
      REDIS_PORT: 36002
      WORK_UNIT_FETCH_COUNT: 50000
      WORK_UNIT_PUBLISH_COUNT: 10000
      WORK_UNIT_POLL_FREQ: 5000
      CLIENT_PHONE_HOME_FREQ: 60000
      PUBLISH_RESULTS: "false"
    logging:
      driver: loki
      options:
        loki-url: "https://loki.setminusx.com/loki/api/v1/push"
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