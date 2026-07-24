# tadbir-budget-settings

Company-wide settings (**paramétrage**) — a small key/value store the whole application reads.
Keys are seeded (you can't create arbitrary ones); values are validated per key. Reads are open;
updates are admin-only.

---

## Endpoints — `/api/v1/settings`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/settings` | Authenticated | All settings |
| `GET` | `/settings/{key}` | Authenticated | One setting |
| `PUT` | `/settings/{key}` | `ROLE_ADMIN` | Update a known setting's value |

**Response:** `{ key, value, updatedBy, updatedAt }` · **Update body:** `{ value }`.

## Known settings

| Key | Values | Meaning |
|---|---|---|
| `project.terminology` | `PROJECT` \| `PROGRAM` (default `PROJECT`) | How the UI labels projects — "Projet" vs "Programme". The front reads this once and labels the projects feature accordingly. |

Errors: `SETTING_NOT_FOUND` (unknown key — you can't create new keys), `SETTING_INVALID_VALUE`
(value not allowed for that key).

## Dependencies

```
settings
  ├── tadbir-budget-common   (ErrorCode, Roles, SecurityUtils)
  ├── tadbir-budget-dao      (AppSetting + repository)
  ├── spring-boot-starter-web / -validation
  └── lombok
```
