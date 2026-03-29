#!/bin/bash
# EC2 User Data Script for Ramsey Worker Nodes (Amazon Linux 2023)
# This script installs Docker and starts worker containers

set -e

# Update system
dnf update -y

# Install Docker (AL2023 uses dnf, not amazon-linux-extras)
dnf install -y docker
systemctl start docker
systemctl enable docker

# Add ec2-user to docker group
usermod -a -G docker ec2-user

# Install Loki Docker logging plugin
docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions 2>/dev/null || true

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Create working directory
mkdir -p /opt/ramsey
cd /opt/ramsey

# Create docker-compose file
cat > docker-compose.yml << 'EOF'
services:
  ramsey-worker-rust:
    image: benferenchak/ramsey-worker-rust:develop-neoverse-v2
    environment:
      RAMSEY_API_URL: http://www.setminusx.cloud:36000/api/ramsey
      RAMSEY_CAMPAIGN_ID: 1
      REDIS_HOST: www.setminusx.cloud
      REDIS_PORT: 36002
      WORK_UNIT_FETCH_COUNT: 50000
      WORK_UNIT_PUBLISH_COUNT: 10000
      WORK_UNIT_POLL_FREQ: 5000
      CLIENT_PHONE_HOME_FREQ: 60000
      PUBLISH_RESULTS: "false"
      TOP_RESULTS_COUNT: 10
      WORKER_COUNT: 1
    logging:
      driver: loki
      options:
        loki-url: "https://loki.setminusx.cloud/loki/api/v1/push"
        mode: non-blocking
        max-buffer-size: 4m
        loki-retries: 5
        loki-batch-size: 1000
        loki-external-labels: "machine=AWS-EC2,service_name={{.Name}}"
    restart: always
    deploy:
      replicas: 1
      resources:
        limits:
          memory: 1G
EOF

# Pull the latest image
docker pull benferenchak/ramsey-worker-rust:develop-neoverse-v2

# Start workers - scale to match CPU core count
CPU_COUNT=$(nproc)
echo "Detected $CPU_COUNT CPU cores, starting $CPU_COUNT workers"
docker-compose up -d --scale ramsey-worker-rust=$CPU_COUNT

# Log completion
echo "Ramsey workers started at $(date)" >> /var/log/ramsey-startup.log
