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

A project is followed **step by step** through phases (`project_phase`, Envers-audited). A phase can
have **sous-phases** — a sous-phase is a phase with a `parentPhaseId` (two levels only), same fields
and same rules. Each phase/sous-phase has a `title`, `description`, `status`, a `weight` (poids — its
share of its parent: a phase's share of the project, a sous-phase's share of its phase) and a
`completion`, a current schedule (`startDate`/`endDate`) and an **immutable baseline**
(`firstStartDate`/`firstEndDate`) used to compute delays. A parent phase's `completion` is **derived**
(Σ non-cancelled sous `weight·completion / 100`, kept denormalized); leaf phases carry their own.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/phases` | Read scope | List top-level phases |
| `GET` | `/phases/{phaseId}` | Read scope | One phase |
| `GET` | `/phases/{phaseId}/subphases` | Read scope | List a phase's sous-phases |
| `POST` | `/phases` | Chef / manager-in-scope | Create a phase (`CREATED`; captures baseline) |
| `POST` | `/phases/{phaseId}/subphases` | Chef / manager-in-scope | Create a sous-phase under a phase |
| `PATCH` | `/phases/{phaseId}` | Chef / manager-in-scope | Update content (any level; blocked once closed) |
| `PATCH` | `/phases/{phaseId}/status` | Chef / manager-in-scope | Change status (any level) |
| `DELETE` | `/phases/{phaseId}` | Chef / manager-in-scope | Remove (any level; cascades to sous-phases) |

Status — `CREATED → ACTIVE → TERMINATED` (forward-only, one step), plus `CREATED/ACTIVE → CANCELLED`
(résilié/annulé). Recording a **positive `completion` on a `CREATED` (leaf) phase auto-starts it**
(→ `ACTIVE`), subject to the same container-active rule (project/parent must be ACTIVE). `TERMINATED` and `CANCELLED` are **terminal** (read-only,
`PROJECT_PHASE_INVALID_STATUS`). Terminating a **leaf** phase sets its `completion` to **100**; a
**parent** keeps its rolled-up completion and requires all its sous-phases closed first
(`PROJECT_PHASE_HAS_OPEN_SUBPHASES`). **Cancelling a parent cascades** to its open sous-phases.
Siblings' (non-cancelled) `weight`s may never sum to more than 100 (`PROJECT_PHASE_WEIGHT_EXCEEDED`).
`endDate ≥ startDate` (`PROJECT_PHASE_INVALID_DATES`). A phase may not start before the project's
`startDate` (`PROJECT_PHASE_START_BEFORE_PROJECT`). A sous-phase can't be nested under a sous-phase
(`PROJECT_SUBPHASE_NESTING`). A **sous-phase's schedule must sit inside its parent phase's schedule**,
and a parent's schedule can't be changed to a window that would exclude one of its sous-phases —
both `PROJECT_SUBPHASE_OUTSIDE_PARENT`. Unknown → `PROJECT_PHASE_NOT_FOUND`.

Status coupling (same logic at each level):
- Created/edited while the project is `NOT_STARTED` (planning) or `ACTIVE`.
- A **top-level phase starts only when the project is `ACTIVE`** (`PROJECT_NOT_ACTIVE`); a **sous-phase
  starts only when its parent phase is `ACTIVE`** (`PROJECT_PHASE_PARENT_NOT_ACTIVE`).
- **All phases must be closed** (`TERMINATED` **or** `CANCELLED`) before the project can be terminated
  (`PROJECT_HAS_OPEN_PHASES`); `terminationDate` ≥ latest non-cancelled phase `endDate`.
- Once the project is `TERMINATED`/`ARCHIVED`, all phase actions are frozen (`PROJECT_INVALID_STATUS`).

### KPIs (computed on the backend — single source of truth, read scope)

Two families, evaluated at `referenceDate` (server "today"). Percentages 0–100 (2 decimals); delays &
durations in days (`> 0` = late / stretched).

Roll-up: a parent phase's `completion` is the weighted roll-up of its non-cancelled sous-phases; the
project then rolls the top-level phases up again. **Cancelled** phases/sous-phases are excluded from
weights and advancement everywhere.

**Project-level** — `GET /api/v1/projects/{id}/kpis` (lean; trivial diffs left to the front). Over the
**non-cancelled top-level** phases, both advancements measure against the whole project (0–100):
- `avancementPlanifie` = **Σ weight** — how much of the project is planned into phases (target ceiling).
- `avancementPondere` = **Σ(weight·completion) / 100** — actual progress (unplanned/cancelled = 0), so
  `pondéré ≤ planifié ≤ 100`. *(e.g. phase weight 10, completion 50 → planifié 10, pondéré 5.)*
- `countCreated`, `countActive`, `countTerminated`, `countCancelled` (total = their sum).
- `phasesEnRetard`.
- `dateFinReference` (max firstEndDate) & `dateFinEstimee` (max endDate) — global delay in days is
  `dateFinEstimee − dateFinReference`.

**Phase-level** — `GET /api/v1/projects/{id}/phases/kpis` → `{ projectId, referenceDate, phases[] }`,
one `PhaseKpi` per **top-level** phase with a nested **`sousPhases[]`** of the same shape. Each carries
`parentPhaseId`, `weight`, `completion`, `completionPlanifiee`, `ecartAvancement`, `contributionPonderee`
(`weight·completion/100`, **0 when cancelled**), `statutDelai`, `retardJours`, `retardDebutJours`,
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
