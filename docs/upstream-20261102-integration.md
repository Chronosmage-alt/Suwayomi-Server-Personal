# Upstream integration — 2 November 2026

Integrated on personal `dev`, starting at `27191f2e`.

## Pinned revisions

- Server: `6b481e8b62938b9c060eb210b934e0fbfdfa466b` to
  `7b061be09c9b62a89a63addd11ac91c40fc2510f` (41 commits).
- Bundled WebUI: r3562 `7d9f8ee679ed0ce5683ea570e3845798bee7d6de` to
  r3572 `29ff36a0d49120f42ef9195b999f356058e5f677` (10 commits).

Server updates include file/download reliability, backup history restore and restore
progress, SyncYomi deltas, KOReader synchronization, MangaBaka tracking, Android
compatibility fixes, authentication CSRF handling and dependency updates. Upstream
removes legacy REST operations; GraphQL and the image/download routes used by the
bundled UI remain. Personal single-owner/no-login behavior, adaptive scheduling,
Completed categories, Komga and library query optimizations are retained.

The upstream KOReader migration is renumbered from M0068 to M0073, after the
personal M0068–M0072 migrations. Existing personal migration names remain stable.
The migration clears obsolete per-user hashes and adds a shared chapter hash;
reading progress is preserved. Hashes are recomputed when needed.

WebUI updates include optional reader border cropping, reading-history page
progress, centralized app/device notifications and the basic-auth deprecation
reminder. Rename-aware merging preserves personal notifier cancellation, bounded
metadata queries, excluded scanlators and initial-import suppression. Browse title
matching and personal settings remain. Packaging markers and smoke expectations
advance to r3572; official WebUI download pins remain independent.

Integration also fixes upstream notification descriptions/body handling and an
initialization cycle (`AppStorage` → helpers → notifications → `AppSession` →
`AppStorage`) by isolating JSON parsing from notification imports. The obsolete
chapter-action success description is removed. Regression tests cover notification
text and the KOReader schema upgrade; existing progress and notifier tests follow
the updated APIs. Frontend test workers are capped at two for predictable memory.

Packaged startup exposed duplicate Android network classes in the upstream stub
JAR. Shadow packaging now keeps the first Android class (the implementation from
AndroidCompat/server), while retaining service-file merging for dependencies.

The container workflow now also runs the upstream Android compatibility tests.

## Validation

- All 548 server tests and five Android compatibility tests passed with no
  failures, errors or skips.
- Server ktlint and shadow JAR packaging passed. Packaged Android network
  classes were verified against the implementation, with no duplicate entries.
- Disposable packaged-runtime smoke passed: database migrations through M0073,
  r3572 bundle detection, single-owner safety, Komga GraphQL operations and
  served JavaScript assets. The temporary runtime was stopped afterward.
- All 94 WebUI tests passed in 22 files.
- TypeScript, lint, formatting, English extraction and GraphQL generation
  against the merged personal server schema passed. Generated base types add
  the upstream download error message; personal fields remain.
- Production Vite/PWA build passed with the existing legacy-minifier fallback
  and chunk-size warnings.

Validation logs and temporary tooling are outside Git at
`C:/Users/bayleeh/.codex/tmp/suwayomi-upstream-20261102`.
No production database, NAS deployment or image publication is included.
