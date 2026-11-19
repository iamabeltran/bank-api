# Deployment guide (own server + own domain)

> This guide has not been tested end to end. Treat it as a checklist and expect to adjust details.
> The API is a demo without authentication: deploy it only with fake data, never with real information.

## What you need

- A small Linux server (a basic 1 vCPU / 1–2 GB VPS is enough) with Docker and Docker Compose installed.
- A domain you control, with an A record (for example `api.yourdomain.com`) pointing to the server's IP.
- Ports 80 and 443 open in the server firewall (Caddy needs both to issue the HTTPS certificate).

## Steps

1. Clone the repository on the server and enter it.
2. Create the environment file and edit the three values (domain and two passwords):
   ```bash
   cp .env.example .env
   nano .env
   ```
3. Build and start MySQL, the API and Caddy:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env up -d --build
   ```
4. Watch the startup (Flyway applies the migrations on the first run):
   ```bash
   docker compose -f docker-compose.prod.yml logs -f app
   ```
5. Check it from your computer:
   - `https://api.yourdomain.com/actuator/health` should answer `{"status":"UP"}`.
   - `https://api.yourdomain.com/swagger-ui.html` opens the interactive documentation.

## Updating

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

## Operating notes

- MySQL is not published to the internet: only the `app` container reaches it, over the internal network.
- Back up the `mysql-data` volume if the data matters (for a demo it usually does not).
- Because there is no authentication, consider limiting access (for example, HTTP basic auth in the `Caddyfile`)
  before sharing the URL widely.
- Add the live URL to the repository description and the README once it works.