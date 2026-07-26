# tadbir-budget-project

Projects, owned by an org unit, run by a chef de projet, with a team. The budget (lignes + amounts,
per year) is a separate module. Whether the UI calls these "Projet" or "Programme" is a company-wide
setting (`project.terminology` in `tadbir-budget-settings`), **not** a per-project field.

---

## Endpoints — `/api/v1/projects`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/projects?status=&orgUnitId=` | Authenticated | Scoped list |
| `GET` | `/projects/{id}` | Authenticated | One project (with team) |
| `POST` | `/projects` | Project creator | Create (**NOT_STARTED**) |
| `PATCH` | `/projects/{id}` | Project creator | Update metadata |
| `PUT` | `/projects/{id}/team` | Project creator | Replace the whole team |
| `POST` | `/projects/{id}/start` | Chef **or** manager-in-scope | → ACTIVE (`{ startDate? }`, defaults today) |
| `POST` | `/projects/{id}/terminate` | Project creator | → TERMINATED (`{ terminationDate }`) |
| `POST` | `/projects/{id}/archive` | Project creator | → ARCHIVED |
| `DELETE` | `/projects/{id}` | Project creator | Delete + its team + documents |

**Create body:** `{ name, objectifs?, description?, chefProjetId, orgUnitId, team?: [{ userId, functionLabel? }] }`
(user refs are **ids**, not uids. `objectifs` ≤ 2000 chars, `description` ≤ 5000.)

### Documents — `/api/v1/projects/{projectId}/documents`

A project has a **documents section** holding files of **any type** (PDF, Office, images, CAD, …).
Bytes live on the filesystem (`tadbir-budget-files`); a `project_document` row keeps the storage key
+ metadata (removed with the project via `ON DELETE CASCADE`).

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/documents` | Read scope | List documents (metadata) |
| `POST` | `/documents` (multipart `file`, optional `label`) | Chef / manager-in-scope | Attach a file |
| `GET` | `/documents/{documentId}` | Read scope | Download the bytes |
| `DELETE` | `/documents/{documentId}` | Chef / manager-in-scope | Remove (row + bytes) |

Accepted types & size come from `files.*` config (broad allow-list, executables excluded, 50 MB/file
by default). Upload on an `ARCHIVED` project → `PROJECT_INVALID_STATUS`; unknown doc → `PROJECT_DOCUMENT_NOT_FOUND`.

### Phases — `/api/v1/projects/{projectId}/phases`

A project is followed **step by step** through phases (`project_phase`, Envers-audited). Each phase has
a `title`, `description`, `status`, a `weight` (poids — its share of the project) and a `completion`
(avancement — its own progress 0→100), a current schedule (`startDate`/`endDate`) and an **immutable
baseline** (`firstStartDate`/`firstEndDate`, captured at creation) used to compute delays.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/phases` | Read scope | List phases (ordered by schedule) |
| `GET` | `/phases/{phaseId}` | Read scope | One phase |
| `POST` | `/phases` | Chef / manager-in-scope | Create (starts `CREATED`; captures baseline) |
| `PATCH` | `/phases/{phaseId}` | Chef / manager-in-scope | Update content (blocked once `TERMINATED`) |
| `PATCH` | `/phases/{phaseId}/status` | Chef / manager-in-scope | Move forward: `CREATED → ACTIVE → TERMINATED` |
| `DELETE` | `/phases/{phaseId}` | Chef / manager-in-scope | Remove (blocked once `TERMINATED`) |

Rules: status is **forward-only, one step at a time** — no skipping, no going back, a `TERMINATED`
phase is read-only (`PROJECT_PHASE_INVALID_STATUS`); terminating a phase sets its `completion` to
**100**. The phases' `weight`s may never sum to more than
100 (`PROJECT_PHASE_WEIGHT_EXCEEDED`). `endDate` ≥ `startDate` (`PROJECT_PHASE_INVALID_DATES`). A phase
may not **start before the project's `startDate`** (checked only once the project has started —
`PROJECT_PHASE_START_BEFORE_PROJECT`). Unknown phase → `PROJECT_PHASE_NOT_FOUND`.

Phase ↔ project-status coupling:
- Phases may be **created/edited while the project is `NOT_STARTED`** (planning) or `ACTIVE`.
- A phase can be **started (`→ ACTIVE`) only when the project is `ACTIVE`** — you can't start a phase
  of a project that hasn't started (`PROJECT_NOT_ACTIVE`).
- **All phases must be `TERMINATED` (closed manually) before the project can be terminated**
  (`PROJECT_HAS_OPEN_PHASES`).
- Once the project is `TERMINATED` (or `ARCHIVED`), **all phase actions are frozen** — no create,
  update, status change or delete (`PROJECT_INVALID_STATUS`). Reads stay open.

### KPIs (computed on the backend — single source of truth, read scope)

Two families, evaluated at `referenceDate` (server "today"). Percentages 0–100 (2 decimals); delays &
durations in days (`> 0` = late / stretched).

**Project-level** — `GET /api/v1/projects/{id}/kpis` (lean, non-redundant headline set; trivial diffs
left to the front). Both advancements measure against the **whole** project (0–100):
- `avancementPlanifie` = **Σ weight** — how much of the project has been planned into phases (the
  target ceiling; climbs to 100 as phases are added).
- `avancementPondere` = **Σ(weight·completion) / 100** — actual progress; unplanned weight counts as
  0, so `pondéré ≤ planifié ≤ 100`. *(e.g. one phase weight 10, completion 50 → planifié 10, pondéré 5.)*
- `countCreated`, `countActive`, `countTerminated` (total = their sum).
- `phasesEnRetard`.
- `dateFinReference` (max firstEndDate) & `dateFinEstimee` (max endDate) — global delay in days is
  `dateFinEstimee − dateFinReference`.

**Phase-level** — `GET /api/v1/projects/{id}/phases/kpis` → `{ projectId, referenceDate, phases[] }`,
one `PhaseKpi` per phase: `weight`, `completion`, `completionPlanifiee`, `ecartAvancement`,
`contributionPonderee` (`weight·completion/100`), `statutDelai`, `retardJours`, `retardDebutJours`,
`dureePlanifieeJours`, `dureeEstimeeJours`, `glissementJours`, and the baseline/current dates.

## Roles & scoping

- **Create/manage** = `IS_PROJECT_CREATOR` (`ADMIN`, `CELL_MANAGER`, `SERVICE_MANAGER`,
  `DEPARTMENT_MANAGER`, `DIRECTION_MANAGER`, `POLE_MANAGER`). **`DIRECTION_GENERALE` does not create —
  it supervises.** (`CELL_MANAGER` is the smallest tier — a *cellule* org unit, below a service.)
- **Org scope (the single checkpoint, `authorizeScope`):** a manager may act only on projects whose
  `orgUnitId` is their **own org unit or a descendant** (subtree); admin, anywhere. *A future
  delegation feature extends only this one method.*
- **Read scope (`authorizeRead`):** anyone whose org unit is the project's unit **or any ancestor up
  the tree** (owning unit + all parents), plus the project's chef and team members, plus `ADMIN` /
  `DIRECTION_GENERALE` / `CONTROLE_GESTION` (who see all). Reads include the project and its documents.
- **Manage (`authorizeManage`, the responsible actions):** the chef de projet (whatever their role)
  **or** a project-creator manager in scope (admin anywhere). Gates `start` and destructive document
  writes (upload / delete). **Team members can see documents but not upload or delete them.**

## Lifecycle & audit

- `NOT_STARTED` (default at creation) → `start` (`ACTIVE` + `startDate`) → `terminate`
  (`TERMINATED` + `terminationDate` — the full end date, not just the year) → `archive` (`ARCHIVED`,
  read-only). `terminate` requires `ACTIVE`, all phases `TERMINATED` (`PROJECT_HAS_OPEN_PHASES`), and
  a `terminationDate` **not earlier than the latest phase `endDate`**
  (`PROJECT_TERMINATION_BEFORE_PHASE_END`). Bad transitions → `PROJECT_INVALID_STATUS`.
- `Project` and `ProjectMember` are **Envers-audited** — every change is attributed to its actor
  (uid + IP + timestamp), which is the audit trail delegation will build on. (`project_document` is
  not audited; each row records its uploader + timestamp.)

## Dependencies

```
project
  ├── tadbir-budget-common   (ErrorCode, Roles, SecurityUtils)
  ├── tadbir-budget-dao      (Project, ProjectMember, ProjectDocument, User, OrgUnit + repos)
  ├── tadbir-budget-files    (FileStorageService — document bytes)
  ├── spring-boot-starter-web / -validation
  └── lombok
```
