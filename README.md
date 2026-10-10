# VisionOne

Practice Growth Operating System, by Vision Digital Lab.

VisionOne connects marketing investment to discovery, leads, calls, appointments, business
outcomes and the work Vision Digital Lab performs. The promise it makes to a client is narrow and
specific:

> You run the practice. VisionOne shows you how Vision is growing it.

Phase 1 serves one client, **THRIVE Longevity Center**, and is architecturally ready for a second
without changing the domain model.

---

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 21 |
| Node | 20 or newer |
| Docker Desktop | current, with Compose v2 |

---

## Running it

### 1. Start the infrastructure

```powershell
.\scripts\up.ps1
```

On a machine with `make`, `make up` does the same thing.

This starts PostgreSQL (5432) and Keycloak (8180).

If a native PostgreSQL service already owns 5432 — common on Windows, where the
`postgresql-x64-14` service starts automatically — compose cannot bind and the stack fails to
come up. Publish it elsewhere and point the API at the same port:

```powershell
$env:VISIONONE_DB_HOST_PORT = "5433"
docker compose -f infra/docker-compose.yml up -d
# then start the API with VISIONONE_DB_URL=jdbc:postgresql://localhost:5433/visionone
```

Only the host side moves. Everything inside the compose network still talks to 5432. Keycloak imports the `visionone` realm from
`infra/keycloak/visionone-realm.json` on first start, so authentication is version-controlled
rather than clicked together.

**Two containers, not four.** There is no message broker. Postgres is the queue: events leaving
VisionOne wait in `outbox_event`, webhooks arriving from a provider wait in `inbox_event`, and a
poller drains each. To see what is pending:

```sql
select event_type, attempts, last_error from outbox_event where published_at is null;
select source, event_type, attempts, last_error from inbox_event where processed_at is null;
```

### 2. Start the API

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Flyway migrates on boot and, under `local` and `demo`, loads the synthetic THRIVE dataset.

### 3. Start the frontend

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

### Sign in

| User | Password | Role | Sees |
| --- | --- | --- | --- |
| `garyadams` | `thrive123` | CLIENT_OWNER | The client experience |
| `sahil` | `vision123` | VISION_ADMIN | The same, plus the admin area from Week 2 |

---

## Layout

```
backend/    Java 21, Spring Boot, modular monolith
frontend/   React, TypeScript, Vite, Tailwind, TanStack, Recharts
infra/      docker-compose, Keycloak realm, Postgres init
docs/       Architecture notes
```

Backend modules are packages under `com.visiondigitallab.visionone`: `auth`, `tenant`, `growth`,
`lead`, `frontdesk`, `appointment`, `work`, `content`, `reporting`, `integration`, `eventing`,
`audit`. Each has `api` (what other modules may use), `domain`, `repository`, `web` and
`internal` (private). The rules are enforced by ArchUnit, not by convention.

---

## Testing

```powershell
cd backend;  .\gradlew.bat test      # unit, ArchUnit, Testcontainers integration, isolation matrix
cd frontend; npm test                # component and formatter tests
```

Integration tests start a real PostgreSQL through Testcontainers and run the real migrations, so
Docker must be running.

---

## Things that are deliberate

- **Money is `bigint` minor units.** No floats, no `BigDecimal` columns, no formatted strings from
  the API.
- **A KPI with no denominator is `null`, not zero.** Cost per lead with no leads renders as an em
  dash. Showing `$0.00` would be flattering and false.
- **Month boundaries use the organization's timezone.** A lead created at 11pm on the 31st belongs
  to that month in the client's reckoning, not the server's.
- **Events are never on the read path.** Every screen is a synchronous read of PostgreSQL. If the
  dispatcher stops, screens and writes carry on and outbox rows accumulate until it runs again.
- **Integration status is shown verbatim.** `DEMO` means synthetic. VisionOne never displays a
  connection as working when it is not.
- **No PHI.** No medical history, diagnoses, labs, medications or clinical notes. `service_interest`
  is a broad category from a fixed list, enforced by a database check constraint. VisionOne is a
  growth platform, not an EHR.

---

## Phase 1 plan

Week 1 foundation and Overview · Week 2 Growth · Week 3 Leads, Work and Content · Week 4 Front
Desk, Reports and polish.

The full architecture and delivery plan lives in the VisionOne project space.
