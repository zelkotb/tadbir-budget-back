# tadbir-budget-nomenclature

Two things: **nomenclature definitions** (level templates) and the real **nomenclatures**
(the filled trees built from a definition).

## Access — role or fine-grained permission

**Both reading and writing** budget config are allowed for: **`ROLE_ADMIN`**, **`ROLE_CONTROLE_GESTION`**
(both manage the whole budget, no permission needed), **or** a user an admin has granted the matching
**permission** (see `Permissions` in `tadbir-budget-common` — a plain-name catalogue like `Roles`, no
prefix):

- **`BUDGET_DEFINITION`** → the nomenclature-definition endpoints.
- **`BUDGET_NOMENCLATURE`** → the nomenclature, rubrique and assignment endpoints.

Permissions let an admin hand budget access to any employee à la carte, without giving them the CG
role — granted via `PATCH /api/v1/user/{id}` `{ "permissions": [...] }` (admin only). The
`usable-rubriques` resolver is the one exception — it stays open to any authenticated user (a project
creator asks "what may I attach to a project"). In the tables below, **"Budget perm"** means *admin or
contrôle de gestion or the matching `BUDGET_*` permission*.

## Nomenclature definitions — the *level templates*

A nomenclature definition is naming/structure only: an ordered list of level names, e.g.
`Chapitre → Article → Paragraphe → Ligne`, or just `Chapitre → Ligne`. The **deepest level is the
leaf** ("ligne budgétaire") that will eventually carry the amounts. It holds no real accounts and
no money.

> **Definition vs nomenclature.** The *definition* is the template (Chapitre → Article → Ligne).
> The real, filled-in tree built from it (Fonctionnement → Marina → Achat progiciel) and its
> amounts are the **nomenclature / budget** — a later concern. This module owns the definitions
> now and will grow to own the real nomenclatures too.

---

## Endpoints — `/api/v1/budget/nomenclature-definitions`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/budget/nomenclature-definitions` | Budget perm | All definitions + their ordered levels |
| `GET` | `/api/v1/budget/nomenclature-definitions/{id}` | Budget perm | One definition |
| `POST` | `/api/v1/budget/nomenclature-definitions` | Budget perm | Create with its level names |
| `PATCH` | `/api/v1/budget/nomenclature-definitions/{id}` | Budget perm | Update; may replace all levels |
| `DELETE` | `/api/v1/budget/nomenclature-definitions/{id}` | Budget perm | Delete a definition + its levels |

**Create body:** `{ "name", "description"?, "levels": ["Chapitre","Article","Paragraphe","Ligne"] }`
**Update body (PATCH, null = unchanged):** `{ "name"?, "description"?, "active"?, "levels"? }`
— when `levels` is present it **replaces** the whole ordered set.

**Response shape:**
```json
{ "id", "name", "description", "active", "depth": 4,
  "levels": [ { "id", "position": 1, "name": "Chapitre", "leaf": false }, …,
              { "id", "position": 4, "name": "Ligne", "leaf": true } ] }
```

## Rules

- `name` unique, case-insensitive (`NOMENCLATURE_DEFINITION_NAME_EXISTS`).
- At least one level (`NOMENCLATURE_DEFINITION_NO_LEVELS`); level names unique within a definition,
  case-insensitive (`NOMENCLATURE_DEFINITION_LEVEL_DUPLICATE`); levels are stored as contiguous
  positions `1..N`, top-down.
- `leaf = true` is always the deepest level (highest position).
- `active = false` retires a definition without deleting it.
- Not Envers-audited (low-churn reference data).

---

## Nomenclatures — the *real filled tree*

A **nomenclature** is the real tree built from a definition — e.g. Fonctionnement → Marina →
Achat progiciel. Its nodes are **rubriques**; leaves (deepest level) are the "lignes budgétaires"
that will later carry the amounts. Lifecycle: **DRAFT** (build the tree) → **FIXED** (locked) →
**ARCHIVED**.

### Endpoints — `/api/v1/budget/nomenclatures`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/nomenclatures` | Budget perm | All nomenclatures (+ definition summary, rubrique count) |
| `GET` | `/nomenclatures/{id}` | Budget perm | One nomenclature |
| `POST` | `/nomenclatures` | Budget perm | Create against a definition (DRAFT) |
| `PATCH` | `/nomenclatures/{id}` | Budget perm | Update name/description |
| `POST` | `/nomenclatures/{id}/fix` | Budget perm | Publish the tree (DRAFT → FIXED) |
| `POST` | `/nomenclatures/{id}/archive` | Budget perm | Retire (→ ARCHIVED) |
| `POST` | `/nomenclatures/{id}/clone?copyAssignments=` | Budget perm | Clone a FIXED version → new DRAFT version |
| `GET` | `/nomenclatures/{id}/versions` | Budget perm | All versions of this lineage |
| `DELETE` | `/nomenclatures/{id}` | Budget perm | Delete a DRAFT nomenclature + its tree |
| `GET` | `/nomenclatures/{id}/rubriques` | Budget perm | Flat rubrique list (build tree from `parentId`) |
| `POST` | `/nomenclatures/{id}/rubriques` | Budget perm | Add a rubrique (DRAFT only) |
| `PATCH` | `/nomenclatures/{id}/rubriques/{rubriqueId}` | Budget perm | Update code/label (DRAFT only) |
| `DELETE` | `/nomenclatures/{id}/rubriques/{rubriqueId}` | Budget perm | Delete a childless rubrique (DRAFT only) |

**Create nomenclature:** `{ "name", "description"?, "nomenclatureDefinitionId" }`
**Create rubrique:** `{ "parentId"?, "code", "label" }` — level & leaf are derived server-side.

### Rules

- A nomenclature conforms to its definition: a top-level rubrique is level 1; a child's level =
  parent level + 1 and may not exceed the definition depth; you can't add a child under a **leaf**
  (`RUBRIQUE_PARENT_IS_LEAF`). `leaf = (level == definitionDepth)`.
- `code` unique among **siblings** (same parent), case-insensitive (`RUBRIQUE_CODE_EXISTS`) — the
  same code may repeat in other branches (e.g. `1` under both Fonctionnement and Investissement),
  but two children of one parent, or two roots, can't share a code.
- Tree writes (add / rename / delete a rubrique) are allowed while **DRAFT or FIXED** — only
  ARCHIVED is read-only (`NOMENCLATURE_ARCHIVED`). FIXED allows **safe evolution**: everything
  references rubriques by id, so **add** and **rename** never break anything; **delete** is refused
  when the rubrique still has children (`RUBRIQUE_HAS_CHILDREN`) or affectations
  (`RUBRIQUE_HAS_ASSIGNMENTS`); structural **moves** are not supported (use a new version).
- `fix` needs at least one rubrique (`NOMENCLATURE_EMPTY`). A nomenclature is **usable** (assignable,
  budgetable) once FIXED.
- Delete a whole nomenclature only while DRAFT (a FIXED one is archived, not deleted).
- **Versioning:** `clone` a FIXED nomenclature into a new DRAFT version of the same **lineage**
  (`lineageId` + `version`, `previousVersionId` linking back). The source is untouched; rubriques are
  copied with fresh ids so old references keep working. Versions share a `name` (uniqueness is
  per-lineage, enforced in the service — the global `name` unique constraint was dropped).
- `definitionDepth` is captured at creation, so the tree stays valid even if the definition
  template is later edited.

---

## Rubrique → org-unit assignment

Which rubriques an org unit (and everything below it) may use when building a project.
CG/admin-managed; many-to-many; assigning a node grants its whole subtree. Only on a **FIXED**
nomenclature.

### Endpoints — under `/api/v1/budget/nomenclatures/{id}`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/assignments?orgUnitId=` | Budget perm | Assignments in this nomenclature (optionally one unit) |
| `POST` | `/assignments` | Budget perm | Assign `{ rubriqueId, orgUnitId }` (nomenclature must be FIXED) |
| `DELETE` | `/assignments/{assignmentId}` | Budget perm | Remove an assignment |
| `GET` | `/usable-rubriques?orgUnitId=` | Authenticated | Rubriques the caller may attach to a project |

**Resolver logic:** a user in org unit *X* can use rubrique *L* when some assignment `(R → U)`
exists with *L* inside *R*'s subtree **and** *U* equal to *X* or an ancestor of *X*. The
`usable-rubriques` endpoint returns the union of the assigned nodes' subtrees for the caller's org
unit (admins may pass `orgUnitId` to preview another unit). Errors: `NOMENCLATURE_NOT_FIXED`,
`RUBRIQUE_ASSIGNMENT_EXISTS`, `RUBRIQUE_ASSIGNMENT_NOT_FOUND`, `RUBRIQUE_WRONG_NOMENCLATURE`,
`ORG_UNIT_NOT_FOUND`.

## Dependencies

```
nomenclature
  ├── tadbir-budget-common   (ErrorCode, Roles, SecurityUtils)
  ├── tadbir-budget-dao      (NomenclatureDefinition(+Level), Nomenclature, NomenclatureRubrique,
  │                           RubriqueAssignment, OrgUnit + repos)
  ├── spring-boot-starter-web / -validation
  └── lombok
```
