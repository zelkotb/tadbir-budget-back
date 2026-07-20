# tadbir-budget-org

Organisation structure — the pôles / directions / départements / services tree, and which unit
each user belongs to. The tree is **freely nested**: `kind` (POLE, DIRECTION, DEPARTEMENT, …) is
a label, not a rule, so a département can hang directly under a pôle, or under a direction —
whatever the company's real structure is.

Each node keeps a **materialized path** (`/rootId/…/nodeId/`), so "the whole subtree of X" is a
single indexed `LIKE` query — the foundation for budget scoping ("a director sees the projects of
their subtree").

---

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/org-units` | Authenticated | Flat list of all units, tree order |
| `GET` | `/api/v1/org-units/{id}` | Authenticated | One unit |
| `GET` | `/api/v1/org-units/{id}/subtree` | Authenticated | The unit + all descendants |
| `GET` | `/api/v1/org-units/{id}/users?subtree=` | Authenticated | Users of the unit (or its whole subtree) |
| `POST` | `/api/v1/org-units` | `ROLE_ADMIN` | Create (root when `parentId` null) |
| `PATCH` | `/api/v1/org-units/{id}` | `ROLE_ADMIN` | Update / move (subtree follows) |
| `DELETE` | `/api/v1/org-units/{id}` | `ROLE_ADMIN` | Delete (guarded) |

**Create body:** `{ "name", "kind", "parentId"?, "managerId"? }`
**Update body (PATCH, null = unchanged):** `{ "name"?, "kind"?, "parentId"?,
"moveToRoot"?, "managerId"?, "clearManager"?, "active"? }`

**Response shape:** `{ id, name, kind, parentId, managerId, managerFullName, path, depth, active }`

## Rules

- Moving: `parentId` may not be the node itself or one of its descendants (`ORG_UNIT_CYCLE`);
  the whole subtree's paths/depths are rebased in one bulk statement.
- Delete is refused while the unit has children (`ORG_UNIT_HAS_CHILDREN`) or assigned users
  (`ORG_UNIT_HAS_USERS`) — re-parent / re-assign first.
- User ↔ unit assignment lives on the **user** (`users.org_unit_id`), managed via the user
  module's create/update endpoints (`orgUnitId` field, admin-only on update).
- `OrgUnit` is `@Audited` (Envers); `path`/`depth` are derived bookkeeping and not audited.

## Dependencies

```
org
  ├── tadbir-budget-common   (ErrorCode, Roles)
  ├── tadbir-budget-dao      (OrgUnit, OrgUnitRepository, User, UserRepository)
  ├── spring-boot-starter-web / -validation
  └── lombok
```
