# Upstream integration — 22 October 2026

Integrated on the personal `dev` branch, preserving adaptive scheduling, historical
learning, Completed categories and Komga integration.

## Pinned upstream revisions

- Server: r2422 `d4f88a34c4a885b801822a772586f196beea144f` to
  `6b481e8b62938b9c060eb210b934e0fbfdfa466b` (13 commits).
- Bundled WebUI: r3549 `8ab2f3385d643f36a415bc8df0c414f86c8a97b1` to
  r3553 `c3465cb4ea15a8778fa6ded50e68bddd18052145` (four commits).
- Personal starting revision: `85c01846`.

The server integration includes H2 category-query optimization and both upstream
follow-up corrections, extension installation and classloader fixes, preference
clear notifications, OPDS authentication/search/feed corrections, dependency
updates and the unstarted-manga timestamp fix. Its sole merge conflict was resolved
by retaining the personal updater and changing its existing not-started predicate
to `(lastReadAt ?: 0L) > 0L`.

The WebUI integration includes continuous vertical/original-scale placeholder
height correction, grouped history thumbnails, and database schema/connection-pool
settings. Both settings already exist in this server. Upstream generated GraphQL
files were excluded; generation uses the custom server schema to preserve personal
fields. English messages are extracted from the integrated source.

The packaged bundle and smoke assertions identify r3553. The generic official
WebUI download pin remains independent of the custom bundle. No migrations,
production database changes or NAS deployment are part of this integration.

## Regression coverage

Upstream updater tests are adapted to the personal five-argument, Boolean-returning
update operation, keeping them source-free. Added GraphQL coverage checks the full
library, category conditions, category equality, categorized/uncategorized filters,
multiple memberships and OR filters, including total counts without duplicates.
Existing adaptive, Completed, Komga, default-category and backup tests remain.

Broader investigation of reported slow loading is deferred until after this
integration. The H2 optimization is included, but no measured improvement is claimed.

## Local validation

- Full server suite: 478 passed, zero failures/errors/skips.
- Server ktlint and shadow JAR build passed using the existing isolated JDK 21.
- All 66 WebUI tests passed; TypeScript, lint, formatting and English extraction
  passed. GraphQL outputs were regenerated from the integrated server's exported
  schema; only operation types changed, retaining all custom types.
- Production Vite/PWA build passed, retaining the existing legacy-minifier fallback
  and chunk-size warnings.
- Headless Chrome at 1440x1000 and 390x844 verified adaptive/Completed controls and
  the new database settings with no overflow, page errors or GraphQL errors.
  Desktop/mobile screenshots were inspected.
- Packaged smoke script passed against a disposable loopback-only runtime copy,
  including r3553, database settings, Komga operations and served JavaScript assets.
- Git whitespace and unresolved-conflict checks passed.

Logs and browser artifacts are outside Git under the existing temporary toolchain
directory and `C:/Users/bayleeh/.codex/tmp/suwayomi-upstream-20261022`. Linux/Java 25
container validation and image publication run in the existing `dev` workflow.

## Publication

The integration merge commit is `9502680fbe9d1f96a3517944df1da516fd3f117f`.
[Actions run 39744851195](https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39744851195)
passed Linux/Java 25 server checks, WebUI checks/build and packaged-container smoke
checks before publishing. Registry manifests and OCI configuration were verified:

- Image: `ghcr.io/chronosmage-alt/suwayomi-server-personal:adaptive-test`
- Immutable tag: `ghcr.io/chronosmage-alt/suwayomi-server-personal:sha-9502680fbe9d`
- Both tags resolve to
  `sha256:21b56e956f1825cd3630da52b3dbeff0075b165947b3e49d9458a3ddb6d60d9b`.
- Both images identify the full integration SHA in their OCI revision label.

Disposable local QA server/proxy processes were stopped. No NAS deployment was
performed. Reported loading performance remains a separate follow-up.
