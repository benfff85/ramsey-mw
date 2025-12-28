# Nginx Reverse Proxy with Let's Encrypt SSL

Reverse proxy for setminusx.com with SSL termination using Let's Encrypt certificates.

## Deployment via Portainer

This stack is deployed via Portainer. The compose file is located at:
`/Users/benferenchak/IdeaProjects/ramsey/ramsey-mw/nginx-proxy/docker-compose.yml`

---

## Initial Setup (First Time Only)

### Step 1: Deploy via Portainer with HTTP-only config

1. Copy `nginx-http-only.conf` to `nginx.conf`:
   ```bash
   cd /Users/benferenchak/IdeaProjects/ramsey/ramsey-mw/nginx-proxy
   cp nginx.conf nginx-ssl.conf.bak
   cp nginx-http-only.conf nginx.conf
   ```

2. Deploy the stack via Portainer using the docker-compose.yml

### Step 2: Obtain the certificate

Run this command to issue the Let's Encrypt certificate:

```bash
docker exec certbot certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email ben.ferenchak@gmail.com \
  --agree-tos \
  --no-eff-email \
  -d www.setminusx.com \
  -d ramsey-ui.setminusx.com \
  -d ramsey-mw.setminusx.com \
  -d portainer.setminusx.com \
  -d openwebui.setminusx.com \
  -d jupyter.setminusx.com
```

Expected output:
```
Successfully received certificate.
Certificate is saved at: /etc/letsencrypt/live/www.setminusx.com/fullchain.pem
Key is saved at:         /etc/letsencrypt/live/www.setminusx.com/privkey.pem
```

### Step 3: Switch to HTTPS config

```bash
cp nginx-ssl.conf.bak nginx.conf
```

### Step 4: Reload nginx

```bash
docker exec nginx-proxy nginx -s reload
```

### Step 5: Verify

Visit https://www.setminusx.com - you should see a valid Let's Encrypt certificate!

---

## Certificate Renewal

Certificates are valid for 90 days. The certbot container automatically attempts renewal every 12 hours.

### Manual Renewal (if needed)

```bash
# Test renewal (dry run)
docker exec certbot certbot renew --dry-run

# Actual renewal
docker exec certbot certbot renew

# Reload nginx to pick up new cert
docker exec nginx-proxy nginx -s reload
```

### Check Certificate Expiry

```bash
docker exec certbot certbot certificates
```

---

## Adding New Subdomains

To add a new subdomain to the certificate:

### Step 1: Update nginx.conf

Add the new server block in `nginx.conf` for both HTTP (ACME challenge) and HTTPS.

### Step 2: Reload nginx

```bash
docker exec nginx-proxy nginx -s reload
```

### Step 3: Expand the certificate

```bash
docker exec certbot certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email ben.ferenchak@gmail.com \
  --agree-tos \
  --no-eff-email \
  --expand \
  -d www.setminusx.com \
  -d ramsey-ui.setminusx.com \
  -d ramsey-mw.setminusx.com \
  -d portainer.setminusx.com \
  -d openwebui.setminusx.com \
  -d jupyter.setminusx.com \
  -d NEW_SUBDOMAIN.setminusx.com
```

### Step 4: Reload nginx again

```bash
docker exec nginx-proxy nginx -s reload
```

---

## Routing

| Domain | Protocol | Backend |
|--------|----------|---------|
| `www.setminusx.com` | HTTPS (redirect from HTTP) | `host.docker.internal:36003` (ramsey-ui) |
| `ramsey-ui.setminusx.com` | HTTPS (redirect from HTTP) | `host.docker.internal:36003` (ramsey-ui) |
| `ramsey-mw.setminusx.com` | HTTP and HTTPS | `host.docker.internal:36000` (ramsey-mw) |
| `portainer.setminusx.com` | HTTPS (redirect from HTTP) | `host.docker.internal:9000` (portainer) |
| `openwebui.setminusx.com` | HTTPS (redirect from HTTP) | `host.docker.internal:11800` (openwebui) |
| `jupyter.setminusx.com` | HTTPS (redirect from HTTP) | `host.docker.internal:8888` (jupyter) |

---

## Commands

### Reload nginx config (without restart)
```bash
docker exec nginx-proxy nginx -s reload
```

### Check nginx config syntax
```bash
docker exec nginx-proxy nginx -t
```

### View nginx logs
```bash
docker logs -f nginx-proxy
```

### View certbot logs
```bash
docker logs -f certbot
```

---

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Docker stack with nginx and certbot |
| `nginx.conf` | Full nginx config (HTTP + HTTPS) |
| `nginx-http-only.conf` | HTTP-only config for initial cert issuance |
| `ssl/` | Let's Encrypt certificates (mounted from certbot) |
| `certbot/www/` | ACME challenge webroot |

---

## Certificate Location

Certificates are stored in the certbot container at:
- Certificate: `/etc/letsencrypt/live/www.setminusx.com/fullchain.pem`
- Private Key: `/etc/letsencrypt/live/www.setminusx.com/privkey.pem`

These are mounted to nginx at `/etc/nginx/ssl/`.

---

## Troubleshooting

### Certificate not found error on startup

If nginx fails to start because certs don't exist yet, use the HTTP-only config first:
```bash
cp nginx-http-only.conf nginx.conf
# ... redeploy via Portainer ...
# ... run certbot ...
# ... restore full config ...
```

### Rate Limits

Let's Encrypt has rate limits:
- 50 certificates per domain per week
- 5 duplicate certificates per week
- Use `--dry-run` for testing!
