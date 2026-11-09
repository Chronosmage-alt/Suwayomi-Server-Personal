# Personal self-hosting cleanup — 9 November 2026

This fork serves one owner using Suwayomi downloads, Komga and external tracking.
The database/application data already use SSD storage; downloaded files use HDD
storage shared with the Komga library. No storage or database change is required.

## Removed automatic work

- No SyncYomi scheduler/config subscription is started during server startup.
- Library updates no longer wait for SyncYomi synchronization.
- Chapter refreshes no longer run SyncYomi deletion/tombstone bookkeeping, which
  discovers sync-enabled users and updates synchronization preferences.
- Local reading/page updates no longer schedule KOReader pushes. Opening chapter
  pages no longer checks/pulls KOReader progress or waits on its remote service.
  Local page/read/bookmark state remains persisted normally.
- OPDS endpoints are not registered in the personal server.
- The WebUI no longer establishes a permanent SyncYomi status subscription or
  mounts sync-status buttons on Library and Updates.
- Server settings no longer request/wait for KOReader and SyncYomi status, and
  their unused controls and OPDS controls are removed.

Integration implementation classes, explicit compatibility GraphQL operations,
existing settings, database tables and backup data remain. They are no longer
invoked automatically by the personal runtime's normal flows. This avoids a
schema/data migration and keeps upstream integrations easier to maintain.

## Preserved

- MyAnimeList, AniList and MangaBaka settings, credentials, bindings, controls and
  manual/automatic progress updates. Tracker code/query behavior is unchanged.
  The binding-refresh regression test now covers all three and checks that
  each is refreshed once rather than repeatedly on rerender.
- Komga extension support, personal Komga controls and API operations.
- Download locations, filenames, CBZ/archive configuration, shared downloaded
  files, automatic downloads and Komga's access to them.
- WebView/KCEF behavior and controls, including its existing initialization.
- H2, owner/no-login behavior, local reading history, adaptive scheduling,
  Completed categories, backups and new-chapter notifications.
- Existing automatic backup schedules and custom bundled-UI update configuration.

The offline H2 import instructions now include the matching
`settings/migrations.xml` from the same stopped database snapshot. Omitting that
file can incorrectly invoke old-engine conversion for a modern H2 database.

## Validation

- All 567 server tests and three Android compatibility tests passed with no
  failures, errors or skips. A new reading regression confirms local progress
  persistence and a tracker update without a KOReader push.
- All 99 frontend tests passed, including the three-provider binding refresh
  regression and startup cancellation/subscription cleanup with three permanent
  subscriptions instead of four.
- TypeScript, lint, formatting, English extraction and production Vite/PWA build
  passed. The existing legacy-minifier fallback and chunk-size warnings remain.
- A fresh disposable packaged runtime passed Komga operations, single-owner API
  safety, r3580 custom-bundle detection and served JavaScript checks. All three
  tracker names were available; the removed OPDS endpoint returned 404.
- Headless Chrome checked Library, Updates, Server settings and Tracking at
  1440x1000 and 390x844. No SyncYomi/KOReader status queries or SyncYomi subscription
  were sent. All three tracker providers and Komga settings remained available;
  there were no browser errors or horizontal overflow. Server/tracking screenshots
  were visually inspected. No production/source/tracker credentials were used.
- Git whitespace checks passed. The task-owned browser and runtime were stopped.

Logs and disposable runtime artifacts are outside Git under
`C:/Users/bayleeh/.codex/tmp/suwayomi-personal-20261109`.
No NAS access, production changes, push or deployment is included.
