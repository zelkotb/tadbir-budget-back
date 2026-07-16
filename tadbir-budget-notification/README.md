# tadbir-budget-notification

A small, reusable **notification service**. Any module hands it a message
("notify this user"), and it delivers it **asynchronously** by **e-mail** and/or as an
**in-app** message (the notification bell) — with rate limiting, automatic retries, and
a full audit trail.

> Sections 1–3 are for product people; 4+ are the engineer reference.

---

## 1. What it does, in plain terms

Sending e-mail is slow and can fail (the mail server may be down or busy). We never want
that to slow down — or break — the action that triggered the message (approving a
request, etc.). So this module works like a **post office with an outbox**:

1. A module drops a message in the **outbox** (a database table). This is instant.
2. A **background worker** picks messages up and delivers them, a few at a time.
3. If delivery fails, it **tries again later**, waiting longer after each failure, and
   eventually gives up after a set number of tries.

Two delivery channels:

- **E-mail** — a real e-mail to the user's address.
- **In-app** — a message shown in the app's notification bell. (This is just a row the
  frontend reads; "delivering" it means making it visible.)

A single event (e.g. "a request awaits your validation") can go out on both channels.

---

## 2. Why a queue with rate limits

Mail providers reject you if you send too fast, and a burst of activity could otherwise
fire hundreds of e-mails at once. So delivery is **throttled**:

| Channel | Default speed |
|---|---|
| E-mail | **2 messages per second** |
| In-app | **10 messages per second** |

Messages above that rate simply wait their turn in the outbox — nothing is lost. Because
the outbox is the database, a server restart loses nothing either: pending messages are
picked up again on the next start.

---

## 3. Audit trail

Every notification row is fully **audited** (Hibernate Envers → `notification_audit`
table). Each change — created, sent, each failed retry, finally read — is recorded with a
timestamp, so you can answer "was this user actually e-mailed, and when?" after the fact.
This mirrors how the `users` table is audited elsewhere in the project.

---

## 4. Every message uses a template

Call sites never write subject/body strings. They pick a **template** and pass a small
**model** (the values to fill in). The module renders it — **HTML** for e-mail, **plain
text** for in-app — so wording and branding live in one place.

Inject `NotificationService` and call `enqueue`:

```java
notificationService.enqueue(NotificationRequest.of(
        user.getId(),
        user.getEmail(),
        Set.of(NotificationChannel.MAIL, NotificationChannel.APPLICATION), // one or both
        NotificationTemplate.WORKFLOW_STEP_ASSIGNED,                       // which template
        Map.of("recipientName", user.getFullName(),                       // model
               "businessKey", businessKey,
               "stepName", "Validation Manager",
               "dueAt", dueAt),
        businessKey));                                                     // referenceKey
```

`enqueue` renders the message now (so the content is captured at send time), writes one
outbox row per channel, and returns — it never sends synchronously. Safe to call inside
your own transaction; the rows commit with it.

### Adding / changing a template

Templates are listed in the `NotificationTemplate` enum. Each entry defines:

| Part | Where | Format |
|---|---|---|
| **subject** | inline string on the enum | Thymeleaf TEXT, e.g. `Votre demande a été [(${outcome})]` |
| **in-app body** | inline string on the enum | Thymeleaf TEXT |
| **e-mail body** | `resources/templates/notification/mail/<name>.html` | Thymeleaf HTML |

To add a template: add an enum constant (subject, in-app body, mail file name) and drop
the matching HTML file in that folder. Placeholders use `${...}` / `[(${...})]` and are
filled from the model map. No other code changes.

The workflow engine already uses this (`WorkflowNotificationListener`) with the
`WORKFLOW_*` templates to notify approvers and requesters — see the workflow module README.

---

## 5. How delivery works (engineer view)

```
NotificationService.enqueue(...)         → INSERT notification rows, status = PENDING

NotificationDispatcher  (@Scheduled, every notification.dispatch.fixed-delay-ms)
   for each channel:
     fetch the oldest due PENDING rows (next_attempt_at <= now, capped at batch-size)
     for each row:
        take a rate-limiter token for the channel (Bucket4j: 2/s mail, 10/s in-app)
            └─ no token left this second → stop; the rest wait for the next tick
        NotificationDeliveryService.deliver(id)   (its own REQUIRES_NEW transaction)
            success → status = SENT,  sent_at = now
            failure → attempts++,
                      attempts >= max-attempts → status = FAILED
                      else                      → stay PENDING,
                                                  next_attempt_at = now + backoff
```

- **Back-off** grows exponentially: `initial-seconds × multiplier^(attempt-1)`
  (default 30s, 60s, 120s, …).
- Each delivery runs in its **own transaction**, so one bad message never affects the others.
- **In-app** delivery has nothing external to call — it just marks the row `SENT`, after
  which it appears in the bell and can later become `READ`.

Lifecycle of a row: `PENDING → SENT → READ` (in-app, when opened), or `PENDING → FAILED`
(retries exhausted).

---

## 6. In-app inbox API (the notification bell)

All endpoints act on the current authenticated user.

| Method & path | Purpose |
|---|---|
| `GET  /api/v1/notifications` | my in-app notifications, newest first (paged) |
| `GET  /api/v1/notifications/unread-count` | badge count (`{ "count": N }`) |
| `POST /api/v1/notifications/{id}/read` | mark one as read |
| `POST /api/v1/notifications/read-all` | mark all my unread as read (`{ "marked": N }`) |

(E-mail needs no API — it lands in the user's mailbox.)

---

## 7. Configuration (`notification.*` in application.yaml)

| Property | Default | Meaning |
|---|---|---|
| `notification.enabled` | `true` | master switch for the background dispatcher |
| `notification.mail.enabled` | `false` | when false, e-mails are **logged, not sent** (dev/test) |
| `notification.mail.from` | `no-reply@tadbir-budget.ma` | From address |
| `notification.mail.per-second` | `2` | e-mail send rate |
| `notification.app.per-second` | `10` | in-app send rate |
| `notification.dispatch.fixed-delay-ms` | `1000` | how often the dispatcher drains the outbox |
| `notification.dispatch.batch-size` | `100` | max rows fetched per channel per tick |
| `notification.backoff.max-attempts` | `5` | give up (FAILED) after this many failures |
| `notification.backoff.initial-seconds` | `30` | first retry delay |
| `notification.backoff.multiplier` | `2.0` | growth factor per retry |

**SMTP** is standard Spring Boot mail config under `spring.mail.*` (host, port,
username, password). It's left blank by default so dev/test boot without a mail server;
set `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` and `MAIL_ENABLED=true` to send for real.

Relies on `@EnableScheduling` / `@EnableAsync`, already enabled on the application.

---

## 8. Design notes & limits

- **Multi-instance safe (ShedLock).** The dispatcher tick is guarded by a distributed
  lock (`@SchedulerLock`, `NotificationSchedulerConfig`, `shedlock` table), so with several
  app instances only **one** drains the queue at a time. This prevents double-sends **and**
  keeps the per-channel rate limits global (one active dispatcher) — important because the
  Bucket4j buckets are per-JVM. If you ever need parallel throughput across nodes instead,
  switch to claim-based delivery (`SELECT … FOR UPDATE SKIP LOCKED`) plus a distributed rate
  limiter (e.g. Bucket4j on Postgres/Redis).
- **Extensible channels.** Add SMS/push by implementing `NotificationChannelSender` and
  adding the channel to `NotificationChannel` — the dispatcher and queue are unchanged.
- **Templating.** E-mail is rendered HTML (Thymeleaf → `MimeMessage`); in-app is rendered
  text. Two dedicated Thymeleaf engines (`NotificationTemplateConfig`) keep this isolated
  from any MVC view rendering. Templates render at enqueue time, so a template bug surfaces
  immediately (and is logged) rather than after the message is queued; transient *delivery*
  failures (SMTP down) still get the full retry/back-off treatment.
