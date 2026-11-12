# Bundled WebUI integration — 12 November 2026

Integrated on personal `dev`, starting at `49c072b51`.

The bundled WebUI advances from r3580
`bd90885b821d894a7cdedf993f987bd9d19caa7f` to r3584
`44b310ef409e6578ce674035c554df04bcaf2dd9` (four commits).
Server upstream remains at `d5215280158b9b78d2e3ca46312e39dccde6b97b`.

Changes fix tracker OAuth redirects with a subpath, subscription registration
without secure-context UUID support, shared unique IDs and restarting dead
GraphQL WebSocket clients. Subscription conflicts use the upstream lifecycle,
which includes the personal unregister-on-unsubscribe behavior. Personal startup
cancellation, retries, no-login access and notification safeguards are retained.
Custom-container revision and packaged smoke expectations advance to r3584.
No server runtime, schema or database changes are included.

Validation:

- Full WebUI run: 22 suites passed, 98 tests passed; the remaining authentication
  suite exposed an incomplete session-storage fixture through the new ID helper.
  After isolating AppSession in that fixture, both authentication and GraphQL
  client suites passed together (six tests). The GraphQL fixture received the
  same isolation. No production storage behavior was changed.
- Added regression checks for insecure-context subscription registration/cleanup
  and deferred WebSocket recreation/restart after disposal and socket opening.
- TypeScript, lint, formatting and English extraction passed.
- Production Vite/PWA build passed; existing legacy-minifier fallback and
  chunk-size warnings remain.

Logs are outside Git at
`C:/Users/bayleeh/.codex/tmp/suwayomi-upstream-20261112`.
No push, container publication or NAS deployment is included.
