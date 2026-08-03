# tadbir-budget-user

User management feature module. Handles admin-driven user creation, password changes, and a paginated Envers-backed audit log of every change made to the `User` entity.

---

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/user` | `ROLE_ADMIN` | Paginated, filterable user list |
| `POST` | `/api/v1/user` | `ROLE_ADMIN` | Create a user and assign roles |
| `PUT` | `/api/v1/user/{id}/password` | Authenticated | Change password |
| `GET` | `/api/v1/user/audit` | `ROLE_ADMIN` | Paginated user change history |

### List — `GET /api/v1/user`

Optional, combinable filters: `fullName`, `email`, `uid` (partial, case-insensitive), `enabled`,
`roles` (any-of), plus:
- **`orgUnitId`** — everyone in that unit **and every unit below it** (subtree). Filtering by a pôle
  returns its directions, their departments, and so on down the tree.
- **`managerId`** — that manager's **direct reports** (users whose N+1 is the given user).

Each row includes the resolved **manager** (`managerId`, `managerUid`, `managerFullName`) and the
resolved **org affectation** (`orgUnitId`, `orgUnitName`) so the UI can show names, not just ids (all
null when unset). `GET /me` and `GET /{id}` resolve both too — so *Mon compte* can show the direct
manager and the org unit.

---

## Create user — `POST /api/v1/user`

**Body:**
```json
{
  "uid":             "p.admin",
  "fullName":        "string",
  "phoneNumber":     "string",
  "email":           "string",
  "password":        "string",
  "roles":           ["ROLE_ADMIN"],
  "permissions":     ["BUDGET_NOMENCLATURE"]
}
```

`uid` is the login identifier (unique). Assignable roles: `ROLE_ADMIN`, `ROLE_EMPLOYEE`,
`ROLE_CELL_MANAGER`, `ROLE_SERVICE_MANAGER`, `ROLE_DEPARTMENT_MANAGER`, `ROLE_DIRECTION_MANAGER`,
`ROLE_POLE_MANAGER`, `ROLE_DIRECTION_GENERALE`, `ROLE_CONTROLE_GESTION`.
Any other value returns `INVALID_ROLE`; a duplicate `uid`/`email` returns `UID_ALREADY_EXISTS` /
`EMAIL_ALREADY_EXISTS`.

**Permissions** (optional, admin-assignable à la carte — independent of roles; see `Permissions` in
`tadbir-budget-common`): `BUDGET_DEFINITION`, `BUDGET_NOMENCLATURE`. They gate the budget
config endpoints (read + write). Set them on create or via `PATCH /api/v1/user/{id}`
`{ "permissions": [...] }` (admin only — ignored on self-update); an unknown value returns
`INVALID_PERMISSION`. Every user response includes the current `permissions` array.

---

## Change password — `PUT /api/v1/user/{id}/password`

**Body:**
```json
{
  "currentPassword": "string",   // required when changing own password
  "newPassword":     "string"
}
```

| Caller | `currentPassword` |
|---|---|
| Admin changing another user's password | Not required |
| Any user changing their own password | **Required** |

---

## User audit — `GET /api/v1/user/audit`

Queries Hibernate Envers revision history for the `User` entity via `AuditReader` (requires `EntityManager`, not a Spring Data repository — Envers has its own query API).

**Query params (all optional):**

| Param | Type | Behaviour |
|---|---|---|
| `performedBy` | `string` | Partial match on the uid of who made the change |
| `ip` | `string` | Partial match on IP address |
| `action` | `CREATE \| UPDATE \| DELETE` | Exact match on revision type |
| `userId` | `UUID` | Exact match — full history of one user |
| `date` | `DD/MM/YYYY` | Filters by that exact day (UTC). Partial input works: `04/06` matches all of 4 June. |
| `page` / `size` / `sort` | standard | Default: 20 records, newest first |

**Response:**
```json
{
  "content": [{
    "revisionId":     1,
    "occurredAt":     "2026-06-05T10:00:00Z",
    "performedBy":    "pm.admin",
    "performedFrom":  "192.168.1.1",
    "action":         "UPDATE",
    "userId":         "uuid",
    "uid":            "j.doe",
    "email":          "user@test.ma",
    "fullName":       "Test User",
    "phoneNumber":    "0612345678",
    "enabled":        true,
    "roles":          ["ROLE_EMPLOYEE"]
  }],
  "totalElements": 42,
  ...
}
```

`password` is never included — it is `@NotAudited` on the `User` entity.

---

## How Envers tracks the actor

`RevInfo` (in `tadbir-budget-dao`) stores the uid and IP of who triggered each revision:

| Scenario | Actor source |
|---|---|
| Admin creates / updates user | `SecurityContextHolder` (authenticated uid) |
| Batch / scheduled job | Falls back to `"system"` |

---

## Dependencies

```
user
  ├── tadbir-budget-common   (ErrorCode, Roles, SecurityUtils, AuditAction)
  ├── tadbir-budget-dao      (User, RevInfo, UserRepository, EntityManager → Envers)
  ├── spring-boot-starter-web
  ├── spring-boot-starter-validation
  └── lombok
```

`spring-boot-starter-data-jpa`, `spring-boot-starter-security`, and `hibernate-envers` arrive transitively from `tadbir-budget-dao`.