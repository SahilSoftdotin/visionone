# Deploying the demo

What this deploys is a **demo**. Six of the seven screens — Growth, Leads, Front Desk, Calendar,
Work & Content, Reports — render from fixtures compiled into the JavaScript bundle. The API is three
endpoints (`/me`, `/meta`, `/overview`), and all four provider ports report `DEMO`. Overview is the
only screen that talks to the backend. That is a fine thing to show a client; it is not the product,
and the UI labels it as such rather than implying otherwise.

One host, four containers, one public port.

```
                     :443
  browser  ──TLS──▶  Caddy  ──┬──▶  /realms/*, /resources/*  ──▶  Keycloak :8180
                              ├──▶  /api/*                   ──▶  API      :8080
                              └──▶  everything else          ──▶  the SPA (static)

                                   Keycloak + API ──▶ Postgres :5432
```

Only Caddy publishes a port. Postgres, Keycloak and the API are reachable on the internal Docker
network and nowhere else, so the database is not exposed because someone forgot a firewall rule.
Everything is one origin, which is why there is no CORS configuration to get wrong.

## What you need first

- A small VPS. 2 vCPU / 4 GB is comfortable; 2 GB works if nothing else runs on it. Ubuntu 24.04.
- Docker Engine and the Compose plugin.
- A hostname — `app.visiondigitallab.com` — with an **A record already pointing at the server's
  IP**. A subdomain rather than a path on the marketing site, deliberately: `visiondigitallab.com`
  runs HubSpot, and a path would share an origin with it, so any script the marketing site loads
  could read the session token out of the portal's storage. A subdomain is a separate origin. Caddy requests the certificate on first start, so DNS has to resolve before then
  or the first boot fails.
- Ports 80 and 443 open. Port 80 is not optional: Let's Encrypt validates over it.

## Deploying

```bash
git clone https://github.com/SahilSoftdotin/visionone.git
cd visionone

cp infra/.env.prod.example infra/.env.prod
# Fill every blank. Generate each secret with: openssl rand -base64 24
nano infra/.env.prod

make deploy
```

`make deploy` renders the realm from `infra/.env.prod`, builds both images, and starts the stack.
First run takes a few minutes: Gradle resolves the backend's dependencies and Caddy waits on the
certificate. Afterwards both layers are cached.

Then open `https://<your DOMAIN>` and sign in as `gary` with the password you set.

## Why the realm is rendered rather than used directly

`infra/keycloak/visionone-realm.json` is the **development** realm. It redirects to
`localhost:5173`, sets `sslRequired: none`, and carries `vision123` / `thrive123` so a fresh clone
can sign in immediately. Every one of those is wrong on a public host.

`scripts/render-realm.py` reads it, substitutes the real domain and the passwords from the
environment, and writes the result to `infra/keycloak/generated/` — which is gitignored. The
committed realm is never modified, so local development keeps working, and no real password is ever
written to a tracked file. The renderer refuses to run on a missing or short password rather than
quietly publishing a weak one.

## Reaching the Keycloak admin console

It is deliberately not routed publicly. It holds every account in the realm and does not need to be
on the internet for a demo. Tunnel to it:

```bash
ssh -L 8180:localhost:8180 user@your-server
docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml \
  exec keycloak /opt/keycloak/bin/kc.sh --version   # confirm it is up
```

then open `http://localhost:8180/admin` with `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD`.

## Updating

```bash
git pull
make deploy
```

Compose rebuilds only what changed. Postgres data lives in a named volume and survives.

Note that the SPA's OIDC settings are **inlined at build time** by Vite — they are not runtime
configuration. Changing `DOMAIN` therefore requires rebuilding the `web` image, which `make deploy`
does anyway.

## No broker, anywhere

Events travel in-process, drained from `outbox_event` by a scheduled poller, so there is nothing to
run here that is not also run locally. This used to need
`SPRING_KAFKA_LISTENER_AUTO_STARTUP=false` to stop the audit consumer retrying `localhost:9092`
once a second on a box with no broker; that workaround is gone with the broker.

If a deployed instance looks like it is not processing events, the queue is a table:

```sql
select event_type, attempts, last_error from outbox_event where published_at is null;
```

## Things this does not do

- **No CI.** Deploys are `git pull && make deploy` on the box. Worth automating once the backend is
  real; automating a pipeline against three endpoints that are about to change is premature.
- **No backups.** It is seeded demo data that Flyway recreates from scratch, so there is nothing to
  lose. This stops being true the moment anyone enters something they care about.
- **No staging environment.** One box, one URL.
