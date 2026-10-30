# Bundled WebUI r3562 integration — 30 October 2026

The bundled personal WebUI advances from official r3553
`c3465cb4ea15a8778fa6ded50e68bddd18052145` to r3562
`7d9f8ee679ed0ce5683ea570e3845798bee7d6de` (nine commits). The upstream
comparison was rechecked on 30 October; this remained the latest master revision.

## Changes

- Chapter lists show the current page for partially read chapters, including an
  unknown-page-count fallback and bounds for stale progress.
- Saved searches created before r3518 convert legacy `position`/`group` filters
  to `positions`. Queries, filter values and already-current filters survive.
  This is an existing WebUI metadata migration, not a SQL schema migration.
- Starting a new migration resets inactive migration state after the existing
  active-migration guard, avoiding a silently ineffective migration button.
- New-chapter notifications respect each manga's excluded scanlators. The personal
  implementation uses a bounded batched metadata-only query rather than upstream's
  library-card query, avoiding chapter summaries and unrelated library fields.
- Nonessential initialization runs in the background after required settings.
  Queue fetching and synchronous-error handling already existed locally. Personal
  generation checks, cancellation, retry limits and subscription cleanup remain.
- Upstream translation and available-language updates are included.

Adaptive/history, Completed categories, single-owner/no-login behavior, Komga,
library query/sort optimizations and browse title matching remain in place.
Notification disabling, incomplete-updater-state filtering and initial-import
suppression are retained. No server runtime or dependency changes are required.

## Generation and packaging

GraphQL outputs were regenerated using `server/build/adaptive-schema.graphql`, the
existing personal server schema. Only operation types changed; the personal base
types and Apollo helpers remained identical. Upstream generated files were not
copied. English extraction retains the custom controls and adds page-progress text.

The custom container's revision marker and packaged smoke expectations now use
r3562. The generic official-WebUI downloader is independent and unchanged.

## Validation

- All 91 frontend tests passed in 21 files, including new saved-search, chapter
  progress, notification filtering/pagination and initializer regressions.
- TypeScript, lint, formatting, GraphQL generation and English extraction passed.
- Production Vite/PWA build passed with the existing legacy-minifier fallback and
  chunk-size warnings.
- Git whitespace checks passed.
- Headless Chrome at desktop 1440x1000 and mobile 390x844 displayed
  `Page: 5/20` and the custom adaptive/Completed library settings without
  horizontal overflow, uncaught browser errors or GraphQL errors. Screenshots
  were visually inspected. Browser QA used a separate copy of the existing
  synthetic library; one fixture chapter received a page count of 20 and page
  progress of 4. Fixture covers are placeholders. No source fetch was initiated.

Test artifacts, downloaded upstream snapshots, the build log and disposable
browser QA runtime are under
`C:/Users/bayleeh/.codex/tmp/suwayomi-webui-20261030` outside Git.
Task-owned QA server and proxy processes were stopped after validation.
No production data or NAS deployment is included. The unchanged server regression
suite and Linux/container pipeline were not run as part of this local WebUI work.
