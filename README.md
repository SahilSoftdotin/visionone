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

This starts PostgreSQL (5432) and Keycloak (8180). Keycloak imports the `visionone` realm from
`infra/keycloak/visionone-realm.json` on first start, so authentication is version-controlled
rather than clicked together.

**Kafka is opt-in.** The API reports readiness `UP` without a broker and its Kafka health
indicator is disabled on purpose, so working on the screens or the API does not need a gigabyte of
RAM sitting idle. Start it when you are exercising the outbox:

```
make up-eventing
# or: docker compose -f infra/docker-compose.yml --profile eventing up -d
```

Kafka UI on :8081 needs the broker too, so it takes both profiles:
`docker compose -f infra/docker-compose.yml --profile eventing --profile tools up -d`

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
| `gary` | `thrive123` | CLIENT_OWNER | The client experience |
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
- **Kafka is never on the read path.** With the broker down, every screen loads and every write
  succeeds; outbox rows drain when it returns.
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
