# tadbir-budget-back

Spring Boot 3.4 / Java 17 backend **base**. A multi-module Maven project that bundles the
cross-cutting concerns most services need — authentication, authorization, rate limiting,
structured logging, notifications, a BPMN workflow engine, file storage and auditing — so a new
project starts from a hardened, wired-together skeleton instead of a blank `main`.

It carries **no business domain of its own**: add your entities, endpoints and BPMN processes on
top of the modules below.

---

## Module structure

```
tadbir-budget-back/
├── tadbir-budget-common/        Shared contracts (ErrorCode, Roles, MDC keys, utilities, validation)
├── tadbir-budget-dao/           Persistence: entities, repositories, Liquibase, Envers audit
├── tadbir-budget-auth-jwt/      JWT authentication + Spring Security config
├── tadbir-budget-user/          User management (CRUD, password, roles, Envers diff audit)
├── tadbir-budget-files/         Generic file storage (traversal-safe), Excel/PDF/JasperReports export
├── tadbir-budget-notification/  Durable notification queue (e-mail + in-app), dispatcher, templating
├── tadbir-budget-workflow/      Flowable engine wrapper: runtime API, listeners, event bridge
└── tadbir-budget-app/           Deployable fat JAR — assembles all modules, config, logging, filters
```

## Dependency graph

```
common ← (no internal deps)
  ↑
dao ← common
  ↑
├── auth-jwt      ← common + dao
├── user          ← common + dao
├── files         ← common
├── notification  ← common + dao
└── workflow      ← common + dao + notification
        ↑
app ← all of the above
```

---

## Tech stack

| Concern | Technology |
|---|---|
| Framework | Spring Boot 3.4.8 / Java 17 |
| Security | Spring Security 6 + JWT (jjwt 0.12); method security `@PreAuthorize` |
| Persistence | Spring Data JPA / Hibernate / PostgreSQL 16 |
| Migrations | Liquibase 4.33 (`db/changelog/master.xml`) |
| Workflow / BPMN | Flowable 7.1 (embedded, shares the datasource; history level `full`) |
| Entity audit | Hibernate Envers (`@Audited`) + custom `RevInfo` (actor uid + IP) |
| Auth event log | `auth_audit` table (LOGIN / LOGOUT / TOKEN_REFRESH) |
| Notifications | Durable queue + background dispatcher, e-mail (SMTP) + in-app, ShedLock-guarded |
| Observability | Micrometer OTel (traceId/spanId in every log line); central `ControllerLoggingAspect` |
| Rate limiting | Bucket4j 8.10 (token-bucket) + Caffeine (per-IP) |
| File storage | Local filesystem, backend-decided paths, path-traversal hardened |
| Reporting / export | Apache POI (`.xlsx`), Flying Saucer (HTML→PDF), JasperReports 6.21 (`.jrxml`→PDF) |
| Build | Maven multi-module |

---

## What you get out of the box

- **JWT authentication** — login/refresh/logout by **uid**, refresh-token rotation, account lockout
  on repeated failures, `auth_audit` event log. Accounts are created by an admin (no self sign-up).
- **Authorization** — role-based method security via `@PreAuthorize` and the `Roles` catalogue:
  a technical `ROLE_ADMIN` plus the organisation hierarchy `ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_MANAGER`,
  `ROLE_DIRECTION_MANAGER`, `ROLE_POLE_MANAGER`, `ROLE_DIRECTION_GENERALE` (swap for your own).
- **Rate limiting** — per-IP token bucket (`RateLimitFilter`), configurable and toggleable per profile.
- **Structured logging** — MDC filter stamps request/trace ids; `ControllerLoggingAspect` logs every
  controller call; Logback profiles per environment; OTel trace/span ids in each line.
- **Notifications** — durable queue with a background dispatcher, multi-channel (SMTP e-mail +
  in-app), Thymeleaf templates, delivery rate-limiting, ShedLock so only one instance dispatches.
- **Workflow** — embedded Flowable engine wrapper with a runtime/definition REST API, assignment
  listeners (sticky, load-balancing, hierarchy resolution) and an event bridge that turns task
  events into notifications. Drop a `*.bpmn20.xml` under `resources/processes/` to deploy it.
- **Files & reports** — traversal-safe local storage with backend-owned layout, plus Excel export,
  HTML→PDF (Flying Saucer) and designed **JasperReports** (`.jrxml`→PDF) helpers.
- **Auditing** — Hibernate Envers field-level history with a custom revision entity capturing the
  acting user's uid and IP.

---

## Running locally

```bash
# Start PostgreSQL
docker run -d --name tadbir-pg \
  -e POSTGRES_DB=TADBIR_BUDGET -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=admin \
  -p 5432:5432 postgres:16

# Run the app (builds upstream modules first)
mvn spring-boot:run -pl tadbir-budget-app -am
```

- Default profile is `dev` (see `tadbir-budget-app/src/main/resources/application-dev.yaml`).
- Liquibase creates the schema on first boot **and seeds a default admin** (`uid=pm.admin`,
  password `P@ss2026` — change it after first login). Flowable auto-deploys any bundled BPMN.
- Profiles: `dev`, `test`, `stage`, `prod` (prod externalizes everything via env vars).
- All other accounts are created by an admin via `POST /api/v1/user` — there is no self sign-up.

### Build

```bash
mvn -DskipTests clean package          # fat jar in tadbir-budget-app/target
mvn verify -Psecurity                  # + OWASP dependency-check (fails on CVSS >= 7)
```

---

## Deployment (Docker)

The image is built on your machine and shipped to the server (no JDK/Maven/source on the server).

| File | Where | Purpose |
|---|---|---|
| `pom.xml` `<version>` | repo root | **The single place the release version is set** |
| `Dockerfile` | repo root | Runtime image (JRE + the pre-built jar) |
| `docker-compose.yml` | repo root | Postgres + backend stack |
| `.env` | repo root (**git-ignored**) | Secrets & config (DB, `JWT_SECRET`, CORS, mail) |
| `deploy/build-and-ship.ps1` | Windows | reads `pom.xml` version → `mvn package` → `docker build`/`save` → `scp -r` |
| `deploy/install-docker.sh` | VM (Rocky) | One-time Docker install + firewall + log rotation |
| `deploy/deploy.sh` | VM | `docker load` + `docker compose up -d` (version derived from the tarball) |

**Flow**

```powershell
# Windows (repo root) — set the version in pom.xml first, then:
./deploy/build-and-ship.ps1            # ships to root@<vm>:/opt/tadbir-<version>/
```
```bash
# VM (Rocky), inside the shipped folder:
cd /opt/tadbir-<version>
sed -i 's/\r//' install-docker.sh deploy.sh   # strip Windows line endings (each copy)
bash install-docker.sh                          # first time only, then: newgrp docker
chmod 600 .env
bash deploy.sh                                   # load image + start
```

### Secrets & configuration (`.env`)

`.env` (and `.env.example`) are **git-ignored** — they hold real credentials. Copy `.env.example`
to `.env` and fill it in. Required keys:

| Key | Meaning |
|---|---|
| `DATABASE_NAME` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres database + credentials |
| `JWT_SECRET` | Base64, ≥ 256-bit — generate with `openssl rand -base64 32` |
| `CORS_ALLOWED_ORIGINS` | Frontend origin(s); unused when the frontend proxies `/api` (same origin) |
| `APP_COOKIE_SECURE` | `false` on plain HTTP, `true` with HTTPS |
| `MAIL_ENABLED` / `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | SMTP (e-mail notifications); `false` = log instead of send |

### First admin

Liquibase seeds one admin on first boot: **uid `pm.admin`, password `P@ss2026`** (`ROLE_ADMIN`).
Log in, change the password (`PUT /api/v1/user/{id}/password`), then create the real users via
`POST /api/v1/user`. To rotate the seeded password directly in the DB instead:

```bash
docker compose exec postgres psql -U "$DATABASE_USERNAME" -d "$DATABASE_NAME" \
  -c "UPDATE users SET password = crypt('YourNewStrongPass', gen_salt('bf',10)) WHERE uid = 'pm.admin';"
```

### Services & ports (single host)

`docker-compose.yml` runs two services (a frontend, if any, is deployed separately):

| Service | Container | Port | Data volume |
|---|---|---|---|
| PostgreSQL | `tadbir-postgres` | internal only | `tadbir_pgdata` |
| Backend API | `tadbir-backend` | `127.0.0.1:8080` (localhost only) | `tadbir_files` |

The backend binds to **localhost** — reachable only on the same host (e.g. behind a frontend that
proxies `/api`), not from the LAN.

### Consulting the logs (on the VM)

Each service writes rolling log files to **`/opt/log/<name>`** on the host (bind mounts):

```bash
sudo tail -f /opt/log/tadbir-budget-backend/application.log   # backend (30-day history)
sudo tail -f /opt/log/postgres/postgresql-$(date +%F).log     # database
grep -i error /opt/log/tadbir-budget-backend/application.log
```

Docker's own container logs are size-capped at 10 MB × 5 (`install-docker.sh`). Log files and data
volumes are shared across version folders (pinned project name `tadbir`), so they survive upgrades.

---

## License

Copyright (c) 2026 Zakaria El Kotb. All rights reserved. See the header in each source file.
