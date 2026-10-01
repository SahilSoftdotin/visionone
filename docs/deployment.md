# Deploying VisionOne

Every screen is served by the API: 30 endpoints across 15 controllers, reading Postgres through the
tenant-scoped repositories. Nothing renders from fixtures compiled into the bundle any more.

What is still standing in is the **data**, not the code. The four provider ports — scheduling,
voice, advertising, analytics — report `DEMO`, and the figures come from `db/demo/R__demo_seed.sql`
rather than from Healthie. Going live is a profile change plus a real adapter, not a rewrite: the
`demo` profile is what pins those ports, and the two connection-status badges in the UI are the only
place the app says so.

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

- A small VPS, Ubuntu 24.04. **2 vCPU / 4 GB is the recommendation; 2 GB genuinely works.** Measured,
  with every screen served and a real sign-in done:

  | Container | Anonymous memory | Total used | `mem_limit` |
  |---|---|---|---|
  | Keycloak | 435 MB | 589 MB | 640m |
  | API | 329 MB | 334 MB | 448m |
  | Postgres | 28 MB | 56 MB | 192m |
  | Caddy + SPA | 17 MB | 21 MB | 64m |
  | **Total** | **809 MB** | **1000 MB** | **1344m** |

  The anonymous column is the one that matters for sizing: it is memory that cannot be reclaimed. The
  difference between it and "total used" is page cache, which the kernel drops under pressure - so
  Keycloak sitting at 92% of its cap is not distress.

  Keycloak is the floor here and it does not move. Its heap is pinned at 192m and its anonymous
  memory is still 435 MB, because ~245 MB of that is metaspace, code cache, thread stacks and Quarkus
  native allocation. Capping it at 448m and 512m were both tried: each ran at 94-97% of cap, survived,
  and had nothing left for a spike.

  CPU was 0.5% across all four while serving, so CPU is not the constraint at one client's traffic.
  Those `mem_limit` lines are not decoration: both JVMs size their heap with
  `-XX:MaxRAMPercentage=70`, which reads the *container* limit only when one exists. Without them
  each would aim at 70% of the whole host and the kernel would kill one of them.

  What argues for 4 GB rather than 2 GB is the **build**, not the app: `make deploy` compiles both
  images on the box while the old stack is still serving. The image build is capped to a 512 MB
  Gradle heap (`GRADLE_OPTS` in `backend/Dockerfile`), verified to produce the jar inside a hard 1 GB
  container, so 2 GB plus the provisioning script's swap does work - it just leans on swap during a
  rebuild. Every figure above is also with the provider ports stubbed; a real Healthie sync will move
  them up.
  Put it in **US East** - THRIVE's organization row is `America/New_York`, and the people using this
  every day are at the practice, not in the timezone administering it.
- Docker Engine and the Compose plugin, a firewall, swap, and log rotation. One script does all of
  it, and is safe to re-run:

  ```bash
  ssh root@<ip> 'bash -s' < scripts/provision-server.sh
  ```

  It opens only 22, 80 and 443. Note that a published Docker port bypasses UFW entirely, so adding
  `ports:` to a service in the compose file puts it on the internet no matter what the firewall
  says - which is exactly why only Caddy publishes anything.
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

It also hardens what Keycloak leaves soft, because the defaults are tuned for a laptop:

| Rendered for production | Why |
|---|---|
| `bruteForceProtected: true`, temporary lockout after 10 failures | Keycloak's default is **off**. Without it the sign-in page is an unlimited password-guessing endpoint the moment it has a public name. Temporary, not permanent: permanent lockout lets anyone lock Gary out of his own dashboard. |
| `directAccessGrantsEnabled: false` | The password grant skips the login page, the theme and the flow. The app uses authorization code + PKCE and never needs it. It stays on locally, where it is how a token is obtained with `curl`. |
| `resetPasswordAllowed: false` while no SMTP is configured | Otherwise "Forgot password?" is rendered on the page and errors when clicked. Two accounts, both provisioned by us; the recovery path is asking us. |
| `post.logout.redirect.uris` rewritten to the real origin | Sign-out is validated against its own list, not `redirectUris`. Miss it and the Sign Out button lands on Keycloak's "Invalid redirect uri" page — a failure that appears only on the deployed host. |

## Why the API is told where the keys are

`VISIONONE_OIDC_JWK_SET_URI` points at `http://keycloak:8180/...` on the internal network, while
`VISIONONE_OIDC_ISSUER` stays the public `https://<DOMAIN>/realms/visionone`.

Both are needed and they are not the same thing. The issuer is what lands in the token's `iss`
claim and it is still validated — set it to the wrong value and every token is rejected. But the API
must not try to *fetch* it: inside the `api` container `localhost` is the `api` container, so
discovery against the public issuer gets `Connection refused` and every request comes back a bare
`401` with a valid token in hand. Even with a real domain it would mean leaving the host over TLS
only to come straight back in through Caddy. Setting `jwk-set-uri` makes Spring skip discovery and
fetch the keys directly, and it keeps applying the issuer validator.

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

`make deploy` finishes by running `make prune`, which drops dangling images and trims the build cache
to 2 GB. That is not tidiness: Docker's build cache is unbounded and reached 6.5 GB on a development
machine after a handful of builds. On a 40 GB disk it is the thing that fills it, and a full disk
takes Postgres down with it. 2 GB is kept so the next deploy still reuses the dependency layers
instead of re-downloading Gradle's whole graph. Only *dangling* images are pruned, so rolling back to
the previously tagged image stays possible.

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

- **No CI.** Deploys are `git pull && make deploy` on the box. Worth automating; with one host and
  one client it is not yet the thing most worth automating.
- **No backups.** Today the data is the seed, which Flyway recreates from scratch, so there is
  nothing to lose. **This stops being true the day Healthie data starts arriving**, and the
  attribution Vision records by hand on the Calendar exists nowhere else at all — Healthie does not
  hold it, so a lost volume loses it permanently. Set up `pg_dump` to somewhere off the box before
  that day, not after it.
- **No staging environment.** One box, one URL.
