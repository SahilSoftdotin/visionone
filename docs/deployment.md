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

  **2 GB is comfortable now that nothing is compiled here.** `make deploy` pulls images GitHub Actions
  already built, so the box never runs Gradle or Vite, and the old argument for 4 GB - needing room to
  compile while still serving - is gone. One shared core is fine too: CPU while serving measured 0.5%.

  `make deploy-build` still exists for building on the box if GitHub is unreachable. That path is
  capped to a 512 MB Gradle heap (`GRADLE_OPTS` in `backend/Dockerfile`) and verified to produce the
  jar inside a hard 1 GB container, so it does work on 2 GB - it just leans on swap and takes twenty
  minutes or more on one core.

  Every figure above is with the provider ports stubbed; a real Healthie sync will move them up.
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

# Package visibility is separate from repository visibility: the repo is public, the images are
# not. One token, one scope: read:packages.
echo <TOKEN> | docker login ghcr.io -u SahilSoftdotin --password-stdin

make deploy
```

`make deploy` renders the realm from `infra/.env.prod`, builds both images, and starts the stack.
First run takes a few minutes: Gradle resolves the backend's dependencies and Caddy waits on the
certificate. Afterwards both layers are cached.

Then open `https://<your DOMAIN>` and sign in as `garyadams` with the password you set.

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
| `resetPasswordAllowed` follows whether SMTP is configured | The link and the mail server move together. Set `VISIONONE_SMTP_PASSWORD` and the realm gets an `smtpServer` block and the link; leave it blank and the link is not rendered at all. There is no state where "Forgot password?" is offered with nowhere to send to, which is what it used to do. |
| `smtpServer` built from the environment | So it survives a realm rebuilt from an empty database. Configuring SMTP by hand in the admin console works until the day the volume is recreated, and then the recovery path is gone exactly when someone needs it. |
| Session timeouts left alone, deliberately | The inactivity rule is enforced in the SPA instead. See "Signing out an idle client" below: Keycloak's own idle timeout cannot do what is wanted here, and does not do what it appears to do either. |
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
on the internet.

An earlier version of this section said to reach it with `ssh -L 8180:localhost:8180`. **That cannot
work**, and it is worth saying why rather than quietly replacing it: Keycloak publishes no port to
the host. Only Caddy does. Forwarding the host's 8180 forwards nothing, because nothing is listening
there - the container is reachable on the Docker network and nowhere else, which is the point.

Use `kcadm.sh` inside the container instead. No tunnel, no exposed port, no restart:

```bash
cd /opt/visionone
set -a; . infra/.env.prod; set +a
C="docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml"

$C exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8180 --realm master \
  --user "$KEYCLOAK_ADMIN_USER" --password "$KEYCLOAK_ADMIN_PASSWORD" </dev/null

# then whatever you came to do, for example
$C exec -T keycloak /opt/keycloak/bin/kcadm.sh get users -r visionone </dev/null
$C exec -T keycloak /opt/keycloak/bin/kcadm.sh set-password \
  -r visionone --username garyadams --new-password '...' </dev/null
```

The `</dev/null` on each one is not decoration: `docker compose exec -T` reads stdin, and without it
the first command swallows the rest of the script.

## Changing a password

Passwords live in Keycloak's database, not in `.env.prod`. The values there seed the realm on **first
import only**, which is why editing them changes nothing on a running system - `--import-realm` skips
a realm that already exists.

So `set-password` above is the way: immediate, no redeploy, no restart. Note that the 8-character
minimum enforced by `scripts/render-realm.py` does **not** apply here - that check runs at deploy
time and governs only what gets seeded.

Keep `.env.prod` in step with any change anyway, so a realm rebuilt from scratch comes back with the
password you expect rather than one nobody remembers.

## Self-service password reset

This works in production. The realm sends through Resend over SMTP, so "Forgot password?" on the
sign-in page emails a one-time link and the user sets their own password without us touching the
server.

Three things had to be true together, and all three are now in `scripts/render-realm.py` rather than
clicked into the admin console, so they survive a realm rebuilt from an empty database:

1. `smtpServer` configured - host `smtp.resend.com:587`, STARTTLS, username the literal `resend`,
   password the Resend API key from `VISIONONE_SMTP_PASSWORD`.
2. `resetPasswordAllowed: true`, which the renderer sets **only** when SMTP is present.
3. Each account's `email` set to an address its owner actually reads, and `emailVerified: true` -
   an unverified address cannot receive a reset. These come from `VISIONONE_DEMO_EMAIL_*` rather
   than the committed realm, because the real addresses are not ours to publish in a public repo.

The sending domain has to be verified with Resend before anything is delivered; `visiondigitallab.com`
is, via a DKIM record at the registrar.

To send someone a set-your-password link without waiting for them to ask:

```bash
echo '["UPDATE_PASSWORD"]' > /tmp/actions.json
docker cp /tmp/actions.json "$($C ps -q keycloak)":/tmp/actions.json
$C exec -T keycloak /opt/keycloak/bin/kcadm.sh update   'users/<USER_ID>/execute-actions-email?client_id=visionone-web&redirect_uri=https://app.visiondigitallab.com/'   -r visionone -f /tmp/actions.json </dev/null
```

**The two query parameters are not optional.** Without them the action token carries no client, so
when the person finishes setting their password Keycloak renders "Your account has been updated"
with no link and no button - a dead end at the end of a flow they were emailed into. `info.ftl`
only offers a way onward when it has `pageRedirectUri`, `actionUri` or `client.baseUrl`, and a
clientless token gives it none of the three. The `redirect_uri` has to match the client's
registered redirect URIs, which `https://app.visiondigitallab.com/` does.

The "Forgot password?" link on the sign-in page does not have this problem: that flow starts from
the client, so the client stays in context throughout.

Keycloak answers with nothing on success. A delivery failure is an `EmailException` in
`docker compose logs keycloak`, so a silent return there means the mail went out.

## Signing out an idle client

A client user is signed out after **15 minutes** without deliberate input, with a 60-second
warning they can dismiss. Vision Admin is not: Vision's own staff work from their own machines in
long sessions, while the client is the one plausibly on a shared front-desk computer.

The numbers come from the API, on `/me`, from `VISIONONE_SESSION_IDLE_MINUTES` and
`VISIONONE_SESSION_WARNING_SECONDS` in `.env.prod`. Changing one is a restart:

```bash
cd /opt/visionone
# edit infra/.env.prod
docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml up -d api
```

No rebuild, no image, no deploy. These were Vite build arguments first, which put them inside the
web bundle and meant changing fifteen minutes to ten needed the whole pipeline; serving them
instead is the only reason this is a restart rather than a release.

A value of zero or less is corrected to the default at startup and logged, on the server and again
in the browser. Zero is the one that matters: it means a deadline of *now*, so every client is
signed out the instant they arrive, repeatedly, with no obvious cause. The warning is capped at
half the window, or it would be on screen before anyone had done anything.

Worth being clear about what this is: advice to the browser, not an access control. It is a
convenience and an honesty measure for an unattended screen. The session itself is bounded by
Keycloak's `ssoSessionMaxLifespan`, and the API authenticates every request independently
regardless of what the browser does with this number. Fifteen minutes is
what a healthcare security questionnaire expects. VisionOne holds no clinical record - an
appointment carries a first name and a last initial and nothing else - but it is handled under a
BAA, so HIPAA's automatic-logoff specification is the bar it gets measured against. That
specification is *addressable* and names no number; clinical systems sit at ten to fifteen
minutes, general dashboards at thirty to sixty.

**Two things about why this is not a Keycloak setting, because both are easy to get wrong.**

The realm has `ssoSessionIdleTimeout: 1800`, and it is tempting to read that as a 30-minute
inactivity logout. It is not one. The SPA sets `automaticSilentRenew: true`, so an open tab
refreshes its token in the background and every refresh is activity against the Keycloak session.
The server's idle clock keeps resetting, so those 30 minutes never elapse while a tab is open. The
protection that appears to be configured is not there. Lowering the number would not help.

And Keycloak cannot express the rule anyway. Session timeouts there are per-realm or per-client,
never per-role, and both accounts sign in through the same `visionone-web` client. A rule that
applies to clients and not to Vision has to live in the application.

What Keycloak still does is bound the worst case: `ssoSessionMaxLifespan: 36000` caps any session
at ten hours regardless of activity, and the SPA keeps its tokens in `sessionStorage`, so closing
the tab ends the session on its own.

The timer compares wall-clock timestamps rather than counting down a `setTimeout`, because timers
do not run while a laptop is asleep - a countdown would resume where it left off and leave someone
signed in after two hours away. It also shares its last-activity time between tabs through
`localStorage`, so a forgotten second tab cannot sign you out of the one you are working in.

An idle sign-out returns to `/login?reason=idle`, which shows what happened instead of handing
straight back to Keycloak. Nothing new had to be registered for that: `{origin}/*` is already a
permitted redirect URI.

## Changing the login theme

`infra/keycloak/themes` is a bind mount, so a theme change needs no image rebuild - `git pull` on
the server is enough to put the new files in front of Keycloak.

It does **not** need `docker compose restart keycloak`, and that is the trap. A restart reuses the
same container, and Keycloak serves theme static resources out of its Quarkus augmentation cache,
which lives in that container's writable layer and survives a restart. The symptom is specific and
confusing: new and changed **templates** are picked up, so a brand new `footer.ftl` renders
immediately, while a changed **stylesheet** keeps serving the old bytes. The file on disk is
correct, the file inside the container is correct, and `/resources/.../visionone.css` still returns
the previous version with `max-age=2592000` on it.

Recreate the container instead:

```bash
cd /opt/visionone
docker compose --env-file infra/.env.prod -f infra/docker-compose.prod.yml   up -d --force-recreate keycloak
```

Safe to do at any time: every piece of Keycloak's state is in Postgres, and `--import-realm` skips
a realm that already exists, so nothing is reset.

Verify by fetching the stylesheet rather than by looking at the page, because the browser caches it
for 30 days and a themed page can look updated while its CSS is a month stale:

```bash
curl -s https://<DOMAIN>/resources/<hash>/login/visionone/css/visionone.css | wc -c
```

The `<hash>` changes between Keycloak versions; read it out of the sign-in page's `<link>` tag.

## Where the images come from

`.github/workflows/images.yml` builds both on every push to `main` that touches `backend/`,
`frontend/` or the workflow itself, and pushes them to GHCR as
`ghcr.io/sahilsoftdotin/visionone-api` and `-web`, tagged with the full commit SHA and `latest`.

The server pulls. It does not compile. That is the whole point: this box is one shared core, and
building a Spring Boot jar on it takes twenty minutes while the old container is still answering the
client. GitHub does the same work in a few minutes, and a private repository gets 2,000 free Linux
minutes a month against roughly 6 per deploy.

**The tests gate the publish.** The backend suite runs against a real Postgres through Testcontainers
on the runner, and the frontend's lint and tests run beside it; a failure means no image is pushed, so
a broken commit cannot reach the server by being deployed. Before this, nothing stopped it.

Two things worth knowing:

- **The web image is specific to one hostname.** Vite inlines the OIDC authority and redirect URI at
  build time, so `app.visiondigitallab.com` is baked in by the workflow. Moving the portal means
  editing the workflow and rebuilding, not changing an environment variable.
- **Rolling back is a tag.** Set `IMAGE_TAG` in `.env.prod` to the previous commit's full SHA and run
  `make deploy`. Nothing is rebuilt, so the rollback is the exact artifact that was running before.

## Updating

```bash
git pull          # for the compose file, the Makefile and the realm - not for code
make deploy       # pulls the images Actions published, then restarts what changed
```

Wait for the workflow to go green before running this, or you will pull the previous `latest`.

Compose restarts only what changed. Postgres data lives in a named volume and survives.

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

- **No automatic deploy.** Pushing to main builds and publishes the images, but nothing tells the
  server to pull them - `make deploy` stays a command someone runs. Deliberate at one client: a
  deploy should be a decision, not a side effect of a commit.
- **No backups.** Today the data is the seed, which Flyway recreates from scratch, so there is
  nothing to lose. **This stops being true the day Healthie data starts arriving**, and the
  attribution Vision records by hand on the Calendar exists nowhere else at all — Healthie does not
  hold it, so a lost volume loses it permanently. Set up `pg_dump` to somewhere off the box before
  that day, not after it.
- **No staging environment.** One box, one URL.
