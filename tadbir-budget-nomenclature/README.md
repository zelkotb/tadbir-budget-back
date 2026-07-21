# tadbir-budget-nomenclature

Budget **nomenclature definitions** — the *level templates* a company's budget classification
follows.

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
| `GET` | `/api/v1/budget/nomenclature-definitions` | Authenticated | All definitions + their ordered levels |
| `GET` | `/api/v1/budget/nomenclature-definitions/{id}` | Authenticated | One definition |
| `POST` | `/api/v1/budget/nomenclature-definitions` | Admin / CdG | Create with its level names |
| `PATCH` | `/api/v1/budget/nomenclature-definitions/{id}` | Admin / CdG | Update; may replace all levels |
| `DELETE` | `/api/v1/budget/nomenclature-definitions/{id}` | Admin / CdG | Delete a definition + its levels |

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

## Dependencies

```
nomenclature
  ├── tadbir-budget-common   (ErrorCode, Roles)
  ├── tadbir-budget-dao      (NomenclatureDefinition, NomenclatureDefinitionLevel + repositories)
  ├── spring-boot-starter-web / -validation
  └── lombok
```
