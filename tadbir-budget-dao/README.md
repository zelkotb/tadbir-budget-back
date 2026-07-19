# tadbir-budget-dao

Persistence layer. Owns everything database-related: JPA entities, Spring Data repositories, Liquibase changelogs, Hibernate Envers infrastructure, and JPA converters. No business logic lives here.

---

## Contents

### Entities (`entity/`)

| Class | Table | Audited | Description |
|---|---|---|---|
| `User` | `users` | ✓ `@Audited` | Application user, implements `UserDetails`. Logs in with `uid`; roles stored as CSV via `StringListConverter`. |
| `RefreshToken` | `refresh_tokens` | — | Refresh token linked to a user |
| `AuthAudit` | `auth_audit` | — | Append-only auth event log (LOGIN/LOGOUT/TOKEN_REFRESH) |
| `RevInfo` | `revinfo` | — | Custom Envers revision entity — stores the actor (uid) + IP of who made the change |

### Enums (`entity/enums/`)

| Class | Used by |
|---|---|
| `AuthEventType` | `AuthAudit` — `LOGIN`, `LOGOUT`, `TOKEN_REFRESH` |
| `NotificationChannel` / `NotificationStatus` | `Notification` — delivery channel & lifecycle state |

### Audit infrastructure (`audit/`)

| Class | Purpose |
|---|---|
| `CustomRevisionListener` | Implements `RevisionListener` — populates `RevInfo` with the actor's uid and IP on every Envers revision. Reads the uid from `SecurityContextHolder`, falling back to `"system"` for unauthenticated / batch operations. |

### Repositories (`repository/`)

| Interface | Entity | Notes |
|---|---|---|
| `UserRepository` | `User` | `findByUid`, `existsByUid`, `existsByEmail`, `findByRoleContaining`, `findStaff` |
| `RefreshTokenRepository` | `RefreshToken` | `findByToken`, `deleteExpiredTokens` |
| `AuthAuditRepository` | `AuthAudit` | extends `JpaSpecificationExecutor` for dynamic filtering |

### Converters (`converter/`)

| Class | Converts |
|---|---|
| `StringListConverter` | `List<String>` ↔ `"ROLE_A,ROLE_B"` — used for `User.roles` column |

### Liquibase changelogs (`src/main/resources/db/changelog/`)

```
master.xml
└── slave/
    └── 2026_07_17_baseline.xml   pgcrypto + pg_trgm, revinfo, users (+ audit),
                                  seed admin (uid=pm.admin), refresh_tokens,
                                  auth_audit, notification (+ audit), shedlock
```

A single clean baseline creates every reusable table. Changelogs live in this module's JAR
resources; `LiquibaseAutoConfiguration` finds `classpath:db/changelog/master.xml` automatically.
**Never edit a deployed changeset — add a new dated one** (e.g. `slave/2026_08_01.xml`).

---

## Envers audit tables

Envers configuration (in `application.yaml`):

```yaml
audit_table_suffix: _audit
revision_field_name: rev
revision_type_field_name: revtype
store_data_at_delete: true
```

| Table | Description |
|---|---|
| `revinfo` | One row per revision: auto-inc `id`, `timestamp` (epoch ms), `actor` (uid), `ip_address` |
| `users_audit` | User state at each revision: `id` + `rev` PK, `revtype` (0=CREATE, 1=UPDATE, 2=DELETE), all non-`@NotAudited` user columns |

`password` is `@NotAudited` — never stored in the audit table.

---

## Dependencies

```
dao
  ├── tadbir-budget-common   (MdcKeys for CustomRevisionListener, shared utils)
  ├── spring-boot-starter-data-jpa
  ├── spring-boot-starter-security   (User implements UserDetails)
  ├── hibernate-envers
  ├── liquibase-core
  └── lombok
```

---

## Adding a new entity

1. Create `entity/MyEntity.java` with `@Entity @Table(name = "my_table")`.
2. Add `@Audited` if changes should be tracked via Envers.
3. Create `repository/MyEntityRepository.java` extending `JpaRepository<MyEntity, UUID>`.
4. Add a new Liquibase changeset `slave/YYYY_MM_DD.xml`. If `@Audited`, also add `my_table_audit` and register the FK to `revinfo`.
5. Register in `master.xml`.

> **Never modify an already-deployed changeset.** Always add a new one.

---

## Naming conventions

- All tables and columns use lowercase snake_case (PostgreSQL folds unquoted identifiers to lowercase).
- Envers audit tables use the `_audit` suffix: `users_audit`, `refresh_tokens_audit`.
- Role name strings must not be substrings of each other — see `Roles.java` in `tadbir-budget-common`.