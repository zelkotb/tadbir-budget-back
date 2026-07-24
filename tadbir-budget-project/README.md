# tadbir-budget-project

Projects, owned by an org unit, run by a chef de projet, with a team. The budget (lignes + amounts,
per year) is a separate module. Whether the UI calls these "Projet" or "Programme" is a company-wide
setting (`project.terminology` in `tadbir-budget-settings`), **not** a per-project field.

---

## Endpoints — `/api/v1/projects`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/projects?status=&type=&orgUnitId=` | Authenticated | Scoped list |
| `GET` | `/projects/{id}` | Authenticated | One project (with team) |
| `POST` | `/projects` | Project creator | Create (ACTIVE) |
| `PATCH` | `/projects/{id}` | Project creator | Update metadata |
| `PUT` | `/projects/{id}/team` | Project creator | Replace the whole team |
| `POST` | `/projects/{id}/terminate` | Project creator | → TERMINATED (`{ year }`) |
| `POST` | `/projects/{id}/archive` | Project creator | → ARCHIVED |
| `DELETE` | `/projects/{id}` | Project creator | Delete + its team |

**Create body:** `{ name, objectifs?, description?, chefProjetId, orgUnitId, team?: [{ userId, functionLabel? }] }`
(user refs are **ids**, not uids. `objectifs` ≤ 2000 chars, `description` ≤ 5000.)

## Roles & scoping

- **Create/manage** = `IS_PROJECT_CREATOR` (`ADMIN`, `SERVICE_MANAGER`, `DEPARTMENT_MANAGER`,
  `DIRECTION_MANAGER`, `POLE_MANAGER`). **`DIRECTION_GENERALE` does not create — it supervises.**
- **Org scope (the single checkpoint, `authorizeScope`):** a manager may act only on projects whose
  `orgUnitId` is their **own org unit or a descendant** (subtree); admin, anywhere. *A future
  delegation feature extends only this one method.*
- **Read scope:** managers see their subtree's projects; `ADMIN` / `DIRECTION_GENERALE` /
  `CONTROLE_GESTION` see all; a project's chef and members always see it.

## Lifecycle & audit

- `ACTIVE` → `terminate` (`TERMINATED` + `terminationYear`) → `archive` (`ARCHIVED`, read-only).
  Bad transitions → `PROJECT_INVALID_STATUS`.
- `Project` and `ProjectMember` are **Envers-audited** — every change is attributed to its actor
  (uid + IP + timestamp), which is the audit trail delegation will build on.

## Dependencies

```
project
  ├── tadbir-budget-common   (ErrorCode, Roles, SecurityUtils)
  ├── tadbir-budget-dao      (Project, ProjectMember, User, OrgUnit + repos)
  ├── spring-boot-starter-web / -validation
  └── lombok
```
