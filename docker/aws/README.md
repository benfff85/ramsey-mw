# AWS Ramsey Worker Deployment

Configuration files for deploying Ramsey workers on AWS EC2 Spot Instances.

## Files

| File | Description |
|------|-------------|
| `ec2-user-data.sh` | EC2 launch template user data script |

## EC2 Launch Template Setup

1. **Go to** EC2 → Launch Templates → Create launch template
2. **Template name**: `ramsey-worker`
3. **AMI**: Amazon Linux 2023 (arm64)
4. **Instance type**: `c8g.medium` (1 vCPU, 2GB) for testing
5. **Key pair**: Select or create one for SSH access
6. **Security group**: Allow outbound to ports 36000 (MW) and 36002 (Redis)
7. **Advanced details → User data**: Paste contents of `ec2-user-data.sh`

## Launching a Spot Instance

1. **Go to** EC2 → Spot Requests → Request Spot Instances
2. **Launch template**: Select `ramsey-worker`
3. **Target capacity**: 1 (or more)
4. **Allocation strategy**: Lowest price
5. **Set maximum price**: ~$0.01/hr for c8g.medium (on-demand is ~$0.034)
6. **Click** Launch

Or use **Fleet request** for multiple instances across AZs.

## Service Endpoints

Workers connect to:
- **Middleware**: `http://www.setminusx.cloud:36000`
- **Redis**: `www.setminusx.cloud:36002`

## Scaling

Each EC2 instance runs 1 worker container. To scale:
- Launch more Spot instances via Spot Fleet
- Use EC2 Auto Scaling Group with Spot capacity
- Adjust `--scale` based on instance size

## Monitoring

SSH into instance and check:
```bash
sudo docker ps                                   # See running containers
sudo docker logs <container_id>                  # View worker logs
sudo docker stats                                # Resource usage
sudo cat /var/log/ramsey-startup.log        # Startup confirmation
sudo cat /var/log/cloud-init-output.log     # Full user data script output
```

## Manual Start/Stop

```bash
cd /opt/ramsey
docker-compose up -d --scale ramsey-worker-rust=1      # Start
docker-compose down                                    # Stop
```

## Cost Savings

Spot instances typically save 60-90% vs on-demand:
| Instance | Cores | On-Demand | Spot (typical) |
|----------|-------|-----------|----------------|
| c8g.medium | 1 | ~$0.034/hr | ~$0.01/hr |
| c8g.xlarge | 4 | ~$0.136/hr | ~$0.04/hr |
