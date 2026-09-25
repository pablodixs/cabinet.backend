# Media detail summary rollout

The album overview now requests `detailLevel=SUMMARY`. The response keeps the existing media and album detail DTO shape, with `tracks: []` and `releaseVersions: []`; the overview uses the canonical track count and type without needing either collection. The Web and iOS tracklist views request the first cursor page (up to 40 tracks) when rendered and can request more pages. No release-version page is requested until a client has a screen for that data.

## Observable payload change

For an album with `T` persisted tracks and `R` synchronized release versions, `FULL` embeds `T + R` row objects in the detail response; `SUMMARY` embeds zero. Album track reads are now bounded to `limit` (default 20, maximum 40) per response. The exact byte reduction depends on metadata and is not represented as a production measurement because no production payload sample was available during implementation.

## Activation and rollback

1. Deploy the backend first. `detailLevel=FULL` remains the default, and the existing `/tracks` response remains unchanged.
2. Deploy Web and iOS clients that request summary details and cursor pages.
3. Observe detail response bytes, track cursor request volume, API latency, and database query duration through existing logs/metrics before changing defaults for any other clients.
4. To roll back a client, restore its full-detail request or legacy `/tracks` call. To roll back backend code, keep migration `V61`; its two additive indexes are harmless while the legacy route remains in use. Removing those indexes is a separate database change and is not required for application rollback.

Track pages contain optional viewer rating/like state and always send `Cache-Control: private, no-store`. Detail `ETag` values are representation-specific (`FULL` versus `SUMMARY`) and continue to use the logical public resource version rather than hashing the JSON body.
