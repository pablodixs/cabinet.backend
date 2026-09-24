# Background Processing

## Execution model

All asynchronous and scheduled work runs inside the web application process. `@EnableScheduling` is enabled by `NotificationSchedulingConfiguration`. Catalog enrichment and selected domain-derived effects use separate durable PostgreSQL outboxes; there is no external broker or worker service.

Bounded executors are defined:

| Bean | Core/max threads | Queue | Uses |
| --- | --- | --- | --- |
| `externalInfoTaskExecutor` | 2 / 4 | 100 | Availability, ratings, awards, tracked-series synchronization |
| `catalogOutboxTaskExecutor` | 4 / 4 | 0 | Catalog translations, credits, tracks, and seasons |
| `domainOutboxTaskExecutor` | 2 / 2 | 0 | Domain event handlers for community stats, trending snapshots, search documents, and cache invalidation |
| `letterboxdImportExecutor` | 1 / 2 | 20 | Matching and applying Letterboxd import jobs |

Rejected submissions are caught and logged for external-info/award/series scheduling. Letterboxd scheduling submits directly; queue rejection can surface from the event listener.

## Schedule inventory

| Schedule | Time | Behavior |
| --- | --- | --- |
| Catalog outbox | Every second by default | Recovers stale claims, claims only free executor capacity, and dispatches enrichment concurrently. |
| Domain outbox | Every second by default | Claims due domain events with `FOR UPDATE SKIP LOCKED`, recovers stale claims, dispatches with bounded concurrency, and applies jittered exponential retries before moving exhausted events to `DEAD`. Handlers also update local search documents after imports and catalog metadata changes. |
| Community statistics reconciliation | Every 5 minutes by default | Rebuilds a bounded batch of dirty, missing, or older-than-7-day media community projections. Configurable with `MEDIA_COMMUNITY_STATS_RECONCILE_POLL_DELAY`, `MEDIA_COMMUNITY_STATS_RECONCILE_BATCH_SIZE`, and `MEDIA_COMMUNITY_STATS_STALE_AFTER`. |
| Local media search reconciliation | Every 10 minutes by default | Indexes a bounded batch of missing media search documents and refreshes documents older than 30 days. Configurable with `MEDIA_SEARCH_LOCAL_RECONCILE_POLL_DELAY`, `MEDIA_SEARCH_LOCAL_RECONCILE_BATCH_SIZE`, and `MEDIA_SEARCH_LOCAL_STALE_AFTER`. |
| Trending snapshot reconciliation | Every 5 minutes by default | Rebuilds a bounded batch of dirty media, advances a resumable initial backfill, and refreshes active snapshots older than seven days. Configurable with `MEDIA_TRENDING_SNAPSHOT_RECONCILE_POLL_DELAY`, `MEDIA_TRENDING_SNAPSHOT_RECONCILE_BATCH_SIZE`, and `MEDIA_TRENDING_SNAPSHOT_STALE_AFTER`. |
| TMDB catalog changes | Daily at `03:30 America/Sao_Paulo` by default | Scans changed movies and series and queues stale catalog enrichment. Configurable with `CATALOG_TMDB_CHANGES_CRON` and `CATALOG_TMDB_CHANGES_ZONE`. |
| Collection synchronization | Daily at `16:30 America/Sao_Paulo` by default | Queues referenced film collections whose TMDB data is stale. Configurable with `CATALOG_COLLECTION_TMDB_SYNC_CRON` and `CATALOG_COLLECTION_TMDB_SYNC_ZONE`. |
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

### Catalog enrichment

Media imports commit core metadata and a `catalog_outbox` event together. The worker claims rows with `FOR UPDATE SKIP LOCKED`, persists tracks or seasons as soon as the primary provider responds, and finishes optional translation and Wikidata enrichment afterward. Claims left in `PROCESSING` by an interrupted process return to `RETRY` after the configured lock timeout.

MusicBrainz album imports also write `ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED` in that same transaction. The catalog worker pages through Release Group releases after commit and upserts edition metadata in a separate short transaction. This work does not delay canonical album import, does not replace the Release Group identity, and does not create per-edition `Media(TRACK)` rows. It shares catalog outbox retries and stale-claim recovery.

### Domain outbox

Domain mutations write `domain_outbox_events` in the same transaction as the canonical change. The initial event set covers media likes, ratings, completion state, list membership, reviews, diary entries, media imports, and metadata changes. A bounded worker dispatches through `DomainOutboxDispatcher` and registered `DomainEventHandler`s. Delivery is at least once, so handlers must tolerate repeats. Handlers evict community cache entries for the media (and parent album/series), plus detail cache entries after import or metadata changes. Like, rating, completion, and public-list membership events also rebuild the media's community statistics and rating distribution from canonical state. This queue remains separate from `catalog_outbox`, which is still responsible for provider enrichment.

`MediaQueryService.findCommunity()` reads aggregate counts and rating buckets from these projections. It continues to query the indexed recent-liker and recent-completer lists, and the `mediaCommunity` Caffeine cache remains enabled. Child album/series rating totals aggregate the same projections across eligible tracks or episodes.

Ranking-relevant domain events also mark a `media_ranking_snapshot_state` row dirty in the source transaction. The ranking handler rebuilds only that media's daily public activity factors for the last 30 days. The trending endpoint scores and orders these rows in PostgreSQL; the score weights and half-life are configurable. The reconciliation schedule bootstraps media without snapshots and repairs old or dirty snapshots.

The media-search handler responds to `MEDIA_IMPORTED` and `MEDIA_METADATA_CHANGED` by rebuilding that media's per-locale search documents from canonical media, translations, credits, and external references. The scheduled reconciliation backfills existing media in bounded batches and repairs old documents. Search requests never materialize external previews.

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

## Operational status

`GET /v1/status` returns only product health for Catalog, Search, Imports, Metadata sync, and External providers, using `OPERATIONAL`, `DEGRADED`, `DELAYED`, or `OUTAGE`. It omits queue depth, internal job runs, event details, provider metrics, and import counts. Detailed operational data is available only under the admin-protected `/v1/admin/status/*` routes.

Important high-level executions are stored in `background_job_runs` with stable job keys and separate execution and product-health states. Start, completion, and failure writes use independent transactions so a failed scheduled operation remains visible even if its own transaction rolls back. Job-run history older than 30 days is removed during the existing notification retention maintenance. The durable catalog outbox remains a separate fine-grained work queue and is aggregated by status for the endpoint. Domain outbox counts are not yet exposed by this endpoint.

## Multi-instance implications

Running more than one replica is safe for most database writes because of transactions and uniqueness constraints. The catalog and domain outboxes coordinate event claims through PostgreSQL `FOR UPDATE SKIP LOCKED`; claims are committed before dispatch and recovered after the lock timeout. Domain completion, retry, and release are fenced by a unique per-claim token, so an expired worker cannot overwrite the state of a later claim. A handler can still finish its side effect after its lease expires, so handlers remain at-least-once and must be idempotent. Community and ranking handlers rebuild from canonical state; search handlers upsert documents.

The remaining background behaviors are process-local or require separate coordination:

- every replica runs every cron schedule;
- in-flight sets in provider refresh schedulers and the discovery `running` flag only reduce duplicate work on one process; they are not locks or correctness authorities. PostgreSQL state, uniqueness constraints, repeatable reads/rebuilds, and subsequent stale reads/scans determine durable results;
- an after-commit notification signal reaches only SSE clients connected to that same replica;
- non-durable provider refresh work already submitted to an in-memory executor may be lost when its process stops. It is requested again by a later stale read or scheduled tracked-series scan; it is best-effort enrichment, not a durable user mutation;
- Letterboxd import jobs and items are persisted and are recovered on startup; the after-commit event is only a wake-up signal, so more than one replica may submit the same job during recovery. Apply attempts serialize on the item row, failed-state writes are conditional on retryable item state, and final job transitions use compare-and-set semantics. Duplicate matching may repeat provider lookups, but only one worker can transition the job or send its ready notification.

The outboxes coordinate durable work through PostgreSQL across replicas. Caffeine caches and executor limits remain process-local; the current domain cache invalidation handler runs on the replica that claims the event, so other replicas rely on the existing cache TTL and synchronous mutation-path invalidation. Every replica runs scheduled jobs, so scheduled scans must remain bounded and idempotent. Distributed cron locks, shared cache invalidation/pub-sub, and cross-replica SSE delivery are separate deployment decisions and are not configured here.

## Observability

The admin status overview exposes durable outbox and catalog-job depths, oldest pending time, recent scheduled job runs, provider latency/error rates, search fallback/zero-result rates, database pool, cache stats, and community projection lag. Provider and search rates use process-lifetime Micrometer counters and reset when the application process restarts. Event and job drill-down routes return status, attempts, identifiers, and timestamps only. They omit JSON payloads, raw error messages, lock owners, credentials, and authorization data. Actuator exposes the existing Micrometer registry at `/actuator/metrics`. The durable outbox gauges are `cabinet.outbox.pending`, `cabinet.outbox.retry`, `cabinet.outbox.dead`, and `cabinet.outbox.oldest.seconds`, tagged by `queue=domain|catalog`; event attempts, failures, and processing timers are tagged by queue and bounded event type. Snapshots refresh every 30 seconds from indexed PostgreSQL status subsets. Community rebuilds export duration and failure counters, while `cabinet.community.lag.seconds` tracks the oldest dirty marker.

Provider calls through `ExternalMediaProviderRegistry`, plus Wikidata SPARQL and OMDb rating lookups, export request, failure, duration, and rate-limit metrics using provider and operation tags. Search exports end-to-end/stage duration, local result count, provider fallback count, and zero-result count. Each Spring-managed Caffeine cache exports cumulative hit, miss, and eviction statistics plus current estimated size, tagged by cache name. Spring Boot's Hikari binder exposes the active/idle/pending connection gauges and acquisition timer under `hikaricp.connections.*`; no second pool sampler is added. These operational details remain in Actuator rather than expanding the public `/v1/status` response.

Micrometer counters, Hikari/cache gauges, and the recent-search failure marker are process-local. In a multi-instance deployment, an admin request reads metrics from the instance that served it, and public Search health may briefly differ between instances after a search failure. This repository does not configure cluster-wide metric aggregation.
