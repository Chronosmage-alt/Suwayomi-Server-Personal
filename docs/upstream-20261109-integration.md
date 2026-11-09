# Upstream integration — 9 November 2026

Integrated on personal `dev`, starting at `af0f25663`.

## Pinned revisions

- Server: `7b061be09c9b62a89a63addd11ac91c40fc2510f` to
  `d5215280158b9b78d2e3ca46312e39dccde6b97b` (14 commits).
- Bundled WebUI: r3572 `29ff36a0d49120f42ef9195b999f356058e5f677` to
  r3580 `bd90885b821d894a7cdedf993f987bd9d19caa7f` (8 commits).

Server updates include Android-style WebView JavaScript evaluation, renderer
failure reporting and graceful browser closing; OPDS chapter/feed/query fixes;
started-manga filtering that recognizes read flags without timestamps; Android
stub download fixes; AppImage update metadata; basic-auth deprecation and
dependency updates.

The updater conflict combines the read-flag fix with the personal work lock,
preparation tracking, cancellation and retained skipped history. Upstream tests
retain real queue/source coverage and account isolation, with expectations adapted
to the personal skipped-history behavior.

WebUI updates include initialization status/error messages, faster WebSocket
recovery, missing-chapter detection across unrecognized chapter numbers and dynamic
viewport sizing for the iOS PWA. Initialization retains personal no-login access,
generation checks, cancellation of pending startup, capped retry delays,
subscription cleanup and background queue/notifier setup. Status remains
uninitialized until required setup completes. Regression tests cover status/error
recovery, immediate cancellation and late authentication responses.

Adaptive scheduling/history, Completed categories, Komga, browse title matching,
library optimizations and notification safeguards remain in place. The custom
container revision and packaged smoke expectations advance to r3580. No SQL
migration is introduced by these upstream updates.

## Validation

- All 566 server tests and three Android compatibility tests passed with no
  failures, errors or skips. Local validation used a 2 GB Kotlin compiler heap
  after the default 512 MB heap became constrained; project settings are unchanged.

- WebUI: 97 tests covered (96 in the full suite, then all nine initialization
  tests after adding the immediate-stop regression); TypeScript, lint, formatting,
  English extraction and production Vite/PWA build passed. The existing legacy
  minifier fallback and chunk-size warnings remain.
- GraphQL generation against the merged personal schema passed. Generated base
  types only add the upstream basic-auth deprecation annotation; operation types
  and personal fields remain unchanged.
- Server ktlint and shadow JAR packaging passed. A disposable packaged runtime
  migrated a fresh database through M0073 and passed r3580 detection, single-owner
  safety, Komga GraphQL operations, adaptive/Completed settings and served asset
  smoke checks. The temporary runtime was stopped afterward.

Validation logs and temporary tooling are outside Git at
`C:/Users/bayleeh/.codex/tmp/suwayomi-upstream-20261109`.

No production database, NAS deployment, container publication or push is included.
