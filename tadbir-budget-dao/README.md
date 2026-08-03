# tadbir-budget-dao

Persistence layer. Owns everything database-related: JPA entities, Spring Data repositories, Liquibase changelogs, Hibernate Envers infrastructure, and JPA converters. No business logic lives here.

---

## Contents

### Entities (`entity/`)

| Class | Table | Audited | Description |
|---|---|---|---|
| `User` | `users` | ✓ `@Audited` | Application user, implements `UserDetails`. Logs in with `uid`; `roles` **and** `permissions` stored as CSV via `StringListConverter` (both exposed as authorities); belongs to an `org_unit` (nullable). |
| `OrgUnit` | `org_unit` | ✓ `@Audited` | Organisation node (pôle/direction/département…), freely nested via `parent_id` + materialized `path` (`path`/`depth` not audited). |
| `NomenclatureDefinition` | `nomenclature_definition` | — | Budget level template (pre-config): Chapitre→Article→…→Ligne. |
| `NomenclatureDefinitionLevel` | `nomenclature_definition_level` | — | One ordered level of a definition (deepest = leaf). |
| `Nomenclature` | `nomenclature` | — | Real filled tree built from a definition; DRAFT→FIXED→ARCHIVED. |
| `NomenclatureRubrique` | `nomenclature_rubrique` | — | A node of a nomenclature tree (code+label, level, leaf). |
| `RubriqueAssignment` | `rubrique_assignment` | — | Assigns a rubrique to an org unit (many-to-many; grants the subtree). |
| `Project` | `project` | ✓ `@Audited` | Project/program (status, chef, org unit, start date, termination date). NOT_STARTED→ACTIVE→TERMINATED→ARCHIVED. |
| `ProjectMember` | `project_member` | ✓ `@Audited` | A team member of a project (user + function). |
| `ProjectDocument` | `project_document` | — | A file attached to a project (any type); stores the storage key + metadata + uploader. |
| `ProjectPhase` | `project_phase` | ✓ `@Audited` | A phase or sous-phase (self-ref `parent_phase_id`, 2 levels) of a project: title, status (incl. CANCELLED), weight, completion, current + baseline schedule. |
| `AppSetting` | `app_setting` | — | Company-wide key/value setting (paramétrage). |
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
| `UserRepository` | `User` | `findByUid`, `existsByUid`, `existsByEmail`, `findByRoleContaining`, `findStaff`, org-unit lookups |
| `OrgUnitRepository` | `OrgUnit` | subtree via `findByPathStartingWith…`, bulk `rebasePaths` for moves |
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
    ├── 2026_07_17_baseline.xml        pgcrypto + pg_trgm, revinfo, users (+ audit),
    │                                  seed admin (uid=pm.admin), refresh_tokens,
    │                                  auth_audit, notification (+ audit), shedlock
    ├── 2026_07_20_org_structure.xml   org_unit (+ audit), users.org_unit_id
    ├── 2026_07_21_nomenclature_definition.xml  rename budget_tree_* → nomenclature_definition[_level]
    │                                           (upgrade) OR create them fresh (new DB) via preconditions
    ├── 2026_07_22_nomenclature.xml    nomenclature, nomenclature_rubrique (the real tree)
    ├── 2026_07_22_rubrique_sibling_code.xml  rubrique code unique per-sibling (drop whole-tree
    │                                         constraint → two partial indexes)
    ├── 2026_07_23_rubrique_assignment.xml  rubrique_assignment (rubrique ↔ org_unit)
    ├── 2026_07_23_nomenclature_versioning.xml  nomenclature version/lineage/previous_version_id;
    │                                           drop global name-unique (versions share a name)
    ├── 2026_07_23_project.xml         project (+ audit), project_member (+ audit)
    ├── 2026_07_24_settings.xml        app_setting (+ seed project.terminology)
    ├── 2026_07_24_project_drop_type.xml  drop project.type (now a company-wide setting)
    ├── 2026_07_24_project_lifecycle.xml  project.start_date + widen status check (NOT_STARTED)
    ├── 2026_07_26_project_terminate_date.xml  termination_year → termination_date (full date)
    ├── 2026_07_26_project_document.xml   project_document (files of any type, FK cascade)
    ├── 2026_07_26_project_phase.xml      project_phase (+ audit): phases with weight/completion + baseline
    ├── 2026_07_28_project_phase_subphase.xml  phase.parent_phase_id (self-FK, 2 levels) + status CANCELLED
    ├── 2026_08_02_user_permissions.xml   users.permissions (+ audit) + back-fill CG users with budget perms
    └── 2026_08_02_permission_rename.xml  normalize stored permission names (PERM_BUDGET_* → BUDGET_*)
```

> The tree-type tables shipped in an earlier build as `budget_tree_type` / `budget_tree_level`;
> the `2026_07_21` change set renames them in place on already-deployed databases (and creates them
> under the new name on fresh ones) — the earlier change set is intentionally no longer in the
> changelog.

Changelogs live in this module's JAR resources; `LiquibaseAutoConfiguration` finds
`classpath:db/changelog/master.xml` automatically. **Never edit a deployed changeset — add a new
dated one** (e.g. `slave/2026_08_01.xml`); use `<preConditions>` when a change must adapt to
already-deployed vs fresh databases.

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