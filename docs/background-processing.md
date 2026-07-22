# Background Processing

## Execution model

All asynchronous and scheduled work runs inside the web application process. `@EnableScheduling` is enabled by `NotificationSchedulingConfiguration`. There is no external queue or worker service.

Two bounded executors are defined:

| Bean | Core/max threads | Queue | Uses |
| --- | --- | --- | --- |
| `externalInfoTaskExecutor` | 2 / 4 | 100 | Availability, ratings, awards, tracked-series synchronization |
| `letterboxdImportExecutor` | 1 / 2 | 20 | Matching and applying Letterboxd import jobs |

Rejected submissions are caught and logged for external-info/award/series scheduling. Letterboxd scheduling submits directly; queue rejection can surface from the event listener.

## Schedule inventory

| Schedule | Time | Behavior |
| --- | --- | --- |
| Notification SSE heartbeat | Every 25 seconds | Sends `heartbeat: ping` to all process-local connections and removes broken emitters. |
| Notification retention | Daily at `03:20` scheduler/JVM zone | Deletes notifications whose `activityAt` is older than 90 days. |
| Tracked series refresh | Daily at `04:00 America/Sao_Paulo` | Schedules every series with at least one `IN_PROGRESS` library entry. |
| Episode release notification | Daily at `08:00 America/Sao_Paulo` | Finds today's non-special episodes and notifies users tracking the series, unless already watched/notified. |
| Letterboxd expiry/redaction | Minute 17 of every hour | Cancels expired ready jobs and clears sensitive serialized payloads from expired terminal jobs. |

External info and awards do not have periodic full-table scans. They refresh on demand when a read observes missing or stale state.

## After-commit events

### Notification invalidation

Community services write or recompute notifications in their transaction and publish `NotificationChangedEvent`. `NotificationStreamService` listens in `AFTER_COMMIT`, then sends `notifications-changed: refresh`. If the transaction rolls back, no signal is sent.

### Series tracking

When a series becomes `IN_PROGRESS`, `UserMediaService` publishes `SeriesTrackingRequestedEvent`. After commit, the scheduler asynchronously synchronizes the series so the initiating library write remains fast.

### Letterboxd jobs

`LetterboxdImportService` persists the new state and publishes a request to `MATCH` or `APPLY`. The after-commit listener submits the job to the importer executor, preventing a worker from observing a not-yet-committed state transition.

## Stale-while-revalidate workers

External availability/ratings and awards use the following pattern:

1. the read service loads persisted data and synchronization state;
2. if missing, it creates/returns pending state and schedules work;
3. if expired, it returns stale data and schedules work;
4. the scheduler de-duplicates an identical key using an in-memory `inFlight` set;
5. the worker calls the provider outside the caller's request;
6. a dedicated transactional persistence service atomically replaces items and snapshot state;
7. failures persist an error/retry expiry rather than creating a tight retry loop.

Availability de-duplication keys include media, info kind, and region. Award keys include subject type and ID. Series synchronization is de-duplicated by series UUID.

## Letterboxd recovery and partial success

At `ApplicationReadyEvent`, jobs left in `MATCHING` or `IMPORTING` are resubmitted. This is the only explicit restart recovery for executor work.

The matching phase handles provider failure per item and moves it to `NEEDS_REVIEW`. The apply phase invokes `LetterboxdImportApplier` in `REQUIRES_NEW` for each item. Successful items commit independently; failed items are marked and reported. Final job state becomes `COMPLETED` or `COMPLETED_WITH_ERRORS`.

Import safety limits:

- compressed upload: 25 MiB;
- total expanded content: 100 MiB;
- one CSV/ZIP entry: 15 MiB;
- ZIP entries: 1,000;
- films: 50,000.

These complement Spring multipart limits of 25 MB per file and 26 MB per request.

## SSE lifecycle

Each `SseEmitter` has a 30-minute timeout. The defaults allow three streams per user and 1,000 in the process. Limits are configurable and invalid non-positive values fail service construction. An over-limit subscription returns `429` with `SSE_CONNECTION_LIMIT`.

Emitters are stored in concurrent in-memory sets. Completion, timeout, send error, or initial-send failure removes them and releases the process-wide slot.

## Multi-instance implications

Running more than one replica is safe for most database writes because of transactions and uniqueness constraints, but background behavior is not coordinated:

- every replica runs every cron schedule;
- in-flight de-duplication is per replica;
- an after-commit notification signal reaches only SSE clients connected to that same replica;
- queued tasks vanish if the owning process terminates;
- Letterboxd recovery can submit the same durable job from multiple starting replicas unless deployment startup is serialized; state and item checks reduce but do not formally eliminate duplicate execution.

For reliable horizontal scaling, introduce distributed locks for cron jobs, a durable outbox/queue for work, and a shared pub/sub channel for SSE invalidation. The existing REST state remains the authoritative fallback.

## Observability

Workers log provider refresh failures and Letterboxd duration/count metrics. There is no Actuator or metrics registry dependency, so queue depth, executor saturation, cron success, provider latency, and SSE counts are not exported. Production deployments should collect structured logs and add metrics before depending on these jobs for strict delivery guarantees.
