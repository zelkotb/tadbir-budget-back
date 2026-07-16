# tadbir-budget-workflow

The workflow engine — now powered by the embedded **[Flowable](https://www.flowable.com/open-source) BPMN engine**.
Workflows are **BPMN 2.0** process definitions: any graph of human tasks and gateways,
so they can go **forward and backward** (send-back/rework loops), support **claim /
reservation** of tasks, conditions, timers and parallel branches — generically, without
engine code per workflow.

> Replaces the old custom step-chain engine, which was forward-only. The module now exposes
> a thin REST layer over Flowable: deploy definitions, start instances, and drive human
> tasks (inbox / claim / complete).

---

## 1. Concepts (plain language)

| BPMN term | Meaning here |
|---|---|
| **Process definition** | A workflow design (a `.bpmn20.xml` file). Identified by the `<process id>` (the *process key*). |
| **Version** | Each deploy of the same key is a new version. Running instances keep their version. |
| **Process instance** | One running case, started with a **businessKey** (pointer to your record, e.g. a `pa_request` id) + variables. |
| **User task** | A step a human does. Has **candidate groups** (who may claim) and, once claimed, an **assignee** (reserved). |
| **Claim / reservation** | A candidate claims a task → becomes its assignee → others can't act on it until released. |
| **Gateway** | A branch. An *exclusive gateway* routes on a condition — a backward arrow to an earlier task = **send-back**. |
| **Variables** | Data carried by the instance; gateways read them (e.g. `outcome = VALIDATE | RETURN`). |

**Identity mapping:** users are identified by their **email**; **candidate groups are the
role authorities** (`ROLE_INSTRUCTOR`, `ROLE_COMMISSION`, `ROLE_ADMIN`, …). So a BPMN
`flowable:candidateGroups="ROLE_INSTRUCTOR"` means "any instructor may claim this task".

---

## 2. How it's wired

- Dependency: `flowable-spring-boot-starter-process` (in this module). Flowable auto-configures
  on the app's datasource and manages its own `ACT_*` tables (`flowable.database-schema-update=true`).
- Config (in `application.yaml`):
  ```yaml
  flowable:
    database-schema-update: true
    history-level: full
    async-executor-activate: true   # BPMN timers / async jobs
  ```
- **Bundled BPMN auto-deploys** from `classpath:/processes/*.bpmn20.xml` on startup.
  This base project ships **no** process of its own — drop your `*.bpmn20.xml` under
  `tadbir-budget-workflow/src/main/resources/processes/` and it deploys on the next start.

---

## 3. Create / update a workflow

A workflow **is** a BPMN file. **Create** and **update** are both a *deploy* (a new version).

### Option A — ship it in the repo (auto-deploy)
Drop a `*.bpmn20.xml` under `tadbir-budget-workflow/src/main/resources/processes/`. It deploys
on the next startup. Best for the standard, version-controlled workflows.

### Option B — deploy at runtime via the API (admin)
```bash
curl -X POST /api/v1/workflow/definitions \
  -H "Authorization: Bearer $JWT_ADMIN" \
  -F "file=@leave-request.bpmn20.xml" -F "name=Demande de congé"
# → { "deploymentId":"…", "definitions":[ {"key":"LEAVE_REQUEST","version":2,...} ] }
```
Re-deploying the same `<process id>` creates **version 2**; in-flight instances stay on v1.

Authoring the BPMN: use the **Flowable Modeler** (visual) or any BPMN editor, or hand-write
it (see the bundled examples). Minimum viable process:
```xml
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://tadbir-budget/workflows">
  <process id="LEAVE_REQUEST" name="Demande de congé" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="managerValidation"/>
    <userTask id="managerValidation" name="Validation Manager" flowable:candidateGroups="ROLE_INSTRUCTOR"/>
    <sequenceFlow id="f2" sourceRef="managerValidation" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>
```

### Modelling the recurring patterns
- **Reservation/claim:** just a `userTask` with `flowable:candidateGroups="ROLE_…"`. Claiming is automatic via the API (`/tasks/{id}/claim`).
- **Forward + send-back:** an `exclusiveGateway` whose outgoing flows test a variable; one points forward, one points **back** to an earlier task:
  ```xml
  <userTask id="directorReview" name="Validation POLE_DIRECTOR"/>
  <sequenceFlow sourceRef="directorReview" targetRef="gw"/>
  <exclusiveGateway id="gw"/>
  <sequenceFlow sourceRef="gw" targetRef="end">
    <conditionExpression xsi:type="tFormalExpression">${outcome == 'VALIDATE'}</conditionExpression>
  </sequenceFlow>
  <sequenceFlow sourceRef="gw" targetRef="instruction">   <!-- send-back -->
    <conditionExpression xsi:type="tFormalExpression">${outcome == 'RETURN'}</conditionExpression>
  </sequenceFlow>
  ```
- **"N+1 of the instructor" (built-in, generic):** attach the reusable listener
  `${managerResolutionListener}` (event `complete`) to a task. On completion it sets the
  process variable `manager` to the completer's `User.manager_id` (their N+1). The next task
  binds `flowable:assignee="${manager}"` and is reserved for that person:
  ```xml
  <userTask id="instruction" flowable:candidateGroups="ROLE_INSTRUCTOR">
    <extensionElements>
      <flowable:taskListener event="complete" delegateExpression="${managerResolutionListener}"/>
    </extensionElements>
  </userTask>
  <userTask id="directorReview" flowable:assignee="${manager}"
            flowable:candidateGroups="ROLE_POLE_DIRECTOR"/>   <!-- fallback if no manager -->
  ```
  Works for any process — no Java per workflow.
- **SLA / deadline:** attach a **boundary timer event** to a task (e.g. `PT48H`) routing to an
  auto-action — replaces the old SLA sweeper, natively.

---

## 4. Use a workflow (runtime API)

| Method & path | Auth | Purpose |
|---|---|---|
| `POST /api/v1/workflow/instances` | auth | start an instance `{ processKey, businessKey, variables }` |
| `GET  /api/v1/workflow/instances?businessKey=…` | auth | running instances for a record |
| `GET  /api/v1/workflow/tasks/inbox` | auth | tasks assigned to me + ones my roles can claim |
| `POST /api/v1/workflow/tasks/{id}/claim` | auth | reserve a task for me |
| `POST /api/v1/workflow/tasks/{id}/unclaim` | auth | release my reservation |
| `POST /api/v1/workflow/tasks/{id}/complete` | auth | complete with routing variables |
| `POST /api/v1/workflow/definitions` | ADMIN | deploy a BPMN (create / new version) |
| `GET  /api/v1/workflow/definitions` | ADMIN | latest version of each process |
| `GET  /api/v1/workflow/definitions/{key}/versions` | ADMIN | all versions of a process |
| `GET  /api/v1/workflow/definitions/{id}/bpmn` | ADMIN | raw BPMN XML (for a viewer) |

**Start** (a domain service usually does this on submit):
```bash
curl -X POST /api/v1/workflow/instances -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{ "processKey":"LEAVE_REQUEST", "businessKey":"leave-42", "variables": {} }'
```

**Assignee claims & completes, manager validates / sends back:**
```bash
# the actor sees the pool task in their list, claims it (now reserved to them)
curl /api/v1/workflow/tasks/inbox -H "Authorization: Bearer $JWT_ACTOR"
curl -X POST /api/v1/workflow/tasks/TASK_ID/claim -H "Authorization: Bearer $JWT_ACTOR"

# …the actor completes the task:
curl -X POST /api/v1/workflow/tasks/TASK_ID/complete -H "Authorization: Bearer $JWT_ACTOR" \
  -H "Content-Type: application/json" -d '{ "variables": {} }'
# → the next task is auto-assigned to the actor's N+1 (manager), who is notified

# manager sends it back for rework
curl -X POST /api/v1/workflow/tasks/MGR_TASK_ID/complete -H "Authorization: Bearer $JWT_MANAGER" \
  -H "Content-Type: application/json" -d '{ "variables": { "outcome":"RETURN" } }'
# → goes back to the earlier task (rework round)

# manager validates instead → process ends
curl -X POST /api/v1/workflow/tasks/MGR_TASK_ID/complete -H "Authorization: Bearer $JWT_MANAGER" \
  -H "Content-Type: application/json" -d '{ "variables": { "outcome":"VALIDATE" } }'
```

**Domain coupling is one-directional:** the engine only knows the `processKey` + `businessKey`.
Your domain module keeps whatever per-step data it needs (in its own tables) and reacts to
workflow events (no polling) to drive notifications and keep its records in sync. The workflow
just routes who acts when.

---

## 5. Notifications & assignment helpers

**Notifications** (toggle: `workflow.notifications.enabled`):
- *Task assignee* — `WorkflowAssigneeNotifier` (this module) notifies the person a task is
  reserved for. Pool tasks are never broadcast — candidates find them in their own inbox.

**Assignment listeners** (attach in the BPMN, no Java per workflow):
- `${managerResolutionListener}` — resolves the completer's N+1 (`User.manager_id`) into the
  `manager` variable for the next task's `flowable:assignee`.
- `${loadBalancingAssignmentListener}` — assigns to the least-busy member of a candidate group.
- `${completerCaptureListener}` — remembers who completed a step so a rework loop can return to
  the same person ("sticky" assignment).

---

## 6. Notes

- **Roles → groups:** candidate group names (e.g. `ROLE_INSTRUCTOR`, `ROLE_MANAGER`) are just the
  role authorities — assign users those roles in the users table. For an N+1 assignee, set each
  user's `manager_id` to their superior.
- **Visual designer:** `GET …/definitions/{id}/bpmn` returns the raw BPMN XML for a viewer.
- **History:** with `history-level: full`, Flowable's HistoryService exposes the full audit
  of tasks/variables per instance if you need a timeline endpoint.
