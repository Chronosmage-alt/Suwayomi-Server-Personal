# Suwayomi adaptive updates — handover

Updated 19 October 2026. This document is context for a fresh Codex conversation.

## Current branch: dev (permanent development branch)

The user requested renaming investigate/adaptive-library-updates to dev for all
future custom Suwayomi Server and bundled WebUI work. Starting tree was clean;
local and remote old branch both pointed to 6a063c71ab5f0df13a515b02473bf87914cea0f4,
and neither a local nor remote dev branch existed. Local rename preserves every
commit, tree and reflog; no history rewrite is involved. All r3549, adaptive,
historical provenance, Completed-category, Komga and PostgreSQL work is retained.

The active GitHub Actions branch trigger is now dev. Image names/tags remain
ghcr.io/chronosmage-alt/suwayomi-server-personal:adaptive-test and sha-<12-char-SHA>.
Active build/integration documentation uses dev and the bundled webui/ source.
Historical snapshots below deliberately retain their original branch names,
commit IDs and permissions; they are records, not instructions to switch back.
The separate historical WebUI checkout and extension repository are untouched.

In progress: validate branch-trigger/docs-only changes, commit them, push dev with
upstream origin/dev, and verify the workflow starts on dev. The old remote branch
is retained as an archive; deleting it was not requested. No default-branch change,
master merge, force push, NAS access or deployment is authorized or performed.
Next session must inspect this top section and git status/branch before resuming.

## Publication complete: combined r3549 and Komga Linux image

The identity repair and publication succeeded. GitHub privacy remains enabled.
Repository-local author/committer identity is the user-supplied verified address
283986614+Chronosmage-alt@users.noreply.github.com; GitHub attributes both identities
to Chronosmage-alt. Global Git configuration and other repositories were unchanged.
Only the four previously unpublished commit email headers/descendant parent IDs
were rewritten; trees, messages, names, timestamps and order were verified equal.
Published base 639355fb and its ancestors were never rewritten. Recovery bundles,
handover copies/patches and the old local backup ref remain available below.

Image source commit: 6eec513f241309e92b36c14c5e9151a8863789f4.
Commit-specific tag:
ghcr.io/chronosmage-alt/suwayomi-server-personal:sha-6eec513f2413
Immutable digest pin:
ghcr.io/chronosmage-alt/suwayomi-server-personal@sha256:86306d189eeb2ea81bcc5e3e1fbb6fece04be34a780e081fbb0a1a1e0336a606
CI: https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39242420411
Run/job conclusion: success. Registry tag digest independently matched the CI
publish digest; registry image configuration independently confirmed linux/amd64
and the exact source-revision label above. The moving adaptive-test tag was also
published; prefer the digest pin when selecting this exact reviewed artifact.
Runtime base used by CI:
ghcr.io/suwayomi/suwayomi-server@sha256:9397e89561c3d0d05bd8657b89808bb306f4a228ca3d055b81c471cee834af41

Linux/Java 25 CI passed 452 server tests, ktlint and shadow JAR build. WebUI passed
50 tests, TypeScript, lint, full formatting and production/PWA build. The actual
packaged image passed adaptive/Completed defaults, all eight Komga GraphQL
operations (including safe disabled/missing-manga guards), no-login/default-user
settings, secret-free settings responses, served JavaScript, all Komga operation
names in the bundled assets and the r3549 revision marker. No live Komga was used.
The previously validated isolated PostgreSQL suite remains 14 prepare + 1 restart
tests; it is not part of Linux CI. Existing build-size/minifier warnings remain
non-blocking. The first run failed only because the new smoke test combined too
many introspection fields; a separate normal follow-up commit split those requests
without weakening the server's introspection protection. The second run passed
and published. All pushes were normal fast-forwards; no force-push/rebase occurred.

M0070 applies automatically on normal startup to the existing configured database,
adding Komga connection/link tables. No manual SQL or database-engine conversion
is required. Komga remains disabled by default; preserve the existing no-login
configuration. To enable it, mount the API-key secret read only into Suwayomi
(default /run/secrets/komga_api_key; optional KOMGA_API_KEY_FILE override), save a
verified server-reachable internal Komga base URL and optional separate browser
URL, run Test Connection, enable, and manually confirm series links. The key is
never entered into WebUI, stored in the DB or included in portable backups.

Remaining NAS checks belong to the user: independently restorable backups,
installed Komga/API-key version, actual network_mode owner and Docker networks,
DNS/LAN routing through Gluetun (do not assume komga:25600 works), readable secret
mount, browser URL, and real API/inventory behavior with disposable Suwayomi data.
The supplied 8080:25600 mapping is not proof of namespace connectivity. Synthetic
PostgreSQL validation is not a real production-library restore rehearsal.
No NAS, production database/downloads/Komga library, extension repository or master
branch was accessed or modified; no deployment occurred. The user will deploy.

This results update is a documentation-only follow-up to the image source above;
it does not alter application content or trigger another image build. Review Git
status and this section before further work. No implementation blocker remains.

## Identity repair completed; normal push succeeded; publication pending CI

Current publication source: 6eec513f241309e92b36c14c5e9151a8863789f4.
The smoke introspection correction was committed with the verified noreply email
and pushed normally after confirming origin had not moved from our prior push.
Local syntax and actual production WebUI asset extraction passed. Production
server/WebUI sources did not change; the new CI run repeats all Linux gates.
The branch is synchronized with origin and the working tree was clean after push.
Monitor the NEW run for 6eec513f rather than failed fd56c926; no published digest
exists yet. Final publication results must be recorded after this run succeeds.
New workflow: https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39242420411
New job ID: 118026171629. Server tests/ktlint/JAR passed; current step is WebUI
validation/build. Packaged smoke and publication still pending.

First Linux run 39241096614 passed server tests/ktlint/shadow JAR and all WebUI
checks/build, but the new packaged smoke test failed: its combined introspection
query used __Type.fields three times and hit GraphQL Java's good-faith limit.
Publication was skipped; no image was published. Corrected only the smoke script
to use separate introspection requests, retaining the server protection unchanged.
Next: validate smoke syntax/queries, commit this CI correction using the noreply
identity, fetch/check no external remote movement, normal push and monitor again.

User supplied the exact GitHub noreply identity:
283986614+Chronosmage-alt@users.noreply.github.com.
It is now configured repository-locally for future commits; global Git identity
and other repositories were not changed. Corrected author AND committer emails on
only the four unpublished commits. Preserved every tree, message, author/committer
name, original timestamp and commit order; only email fields and descendant parent
IDs changed. Published base 639355fb and all its ancestors are unchanged.

Old -> new mapping:
- b952d64e -> ce7e29241e22b681ca2a61d8b6d01addbb7c737c (r3549).
- ec69f7d4 -> 2190007be4beed9222a894e486d3995d8b07098b (Komga).
- fd8b366f -> e73742726f8f9c1d14b989b857d66e13a2297d53 (PostgreSQL harness).
- 8c1f1d17 -> fd56c926da312f60f31d8ae03a23cabca8e026a0 (packaged smoke gates).

Recovery data: C:/Users/bayleeh/.codex/tmp/suwayomi-publication/identity-repair/
contains the complete pre-rewrite working handover, binary patch, verified bundle
and verification.json with all old/new commit and tree IDs. A local backup ref
refs/backup/email-privacy-before-20261019 retains the old head and was NOT pushed.
The uncommitted handover survived byte-for-byte before this new status addition.

Fetched origin immediately before push; it was still 639355fb, ahead 4/behind 0.
Normal push succeeded: origin/investigate/adaptive-library-updates now fd56c926.
No force push, rebase, master merge, production or extension access occurred.
No source changed during identity repair, so previous local test results stand.
Next: monitor the workflow for source fd56c926 through server/WebUI and Linux
packaged Komga smoke checks, then publication. Do not declare an image ready until
CI succeeds. Record run URL, exact source SHA, image SHA tag and digest here.
This handover remains uncommitted while CI results are pending.
Active workflow: https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39241096614
Job ID: 118021810861. Monitor this exact source/run, not an older successful image.
Linux CI progress: server tests, ktlint and shadow JAR build passed. WebUI
validation/build is running; packaged smoke and publication remain pending.

## Identity repair authorized — waiting for exact GitHub noreply address

User authorizes correcting author/committer identities on only the four unpublished
commits, preserving all trees, messages, order and published history; then a normal
push and CI publication monitoring. Keep GitHub privacy enabled. Do not guess the
noreply address; stop and request it if it cannot be established reliably.

Inspection: b952d64e, ec69f7d4, fd8b366f and 8c1f1d17 all use the same personal email
in both author and committer headers. The repository inherits that personal email
and user.name=Chronosmage-alt from C:/Users/bayleeh/.gitconfig. No repository-local
or matching historical noreply identity was found. The connected GitHub profile
is Chronosmage, while this repository/commit identity uses Chronosmage-alt. No
account's noreply address was inferred from that mismatch. Exact user-supplied
GitHub Settings -> Emails noreply address is required before proceeding.

No identity configuration, commit/ref/history change, push or CI run was performed
in this repair attempt. HEAD remains 8c1f1d1711bdf7e02f3b3f8165c860a84192b0e4,
four commits ahead of the last fetched origin 639355fb. Published commits and all
source content are unchanged; only this handover remains modified.

Protected the pre-repair uncommitted handover before any history work:
C:/Users/bayleeh/.codex/tmp/suwayomi-email-repair-918fe42eee1f469cad6808fb807d16ef/
contains its complete file, binary Git patch, base/head/tree IDs and a verified
bundle containing the four unpublished commits (requires base 639355fb).
Original handover SHA256:
059647ECDDB1F00D695FC3C705CDBE183C2C8FAF3BB5B7D9E795107530F60506

Next: obtain the exact intended verified GitHub noreply address from the user.
Then fetch/check origin has not moved; set repository-local identity, reconstruct
only the four unpublished commits with corrected emails, preserving names/dates,
trees/messages/order, and restore/preserve this working handover. Verify every
old/new tree and message plus unchanged published base. Rerun source validation
only if source content changes. Use a normal push only; stop on a remote change or
push rejection. Monitor all Linux gates/publication and record source SHA/tag/digest
only after success. No master, extension repository or NAS/production access.

## Publication approved: combined r3549 and Komga development image

### STOPPED: GitHub rejected the normal push (GH007)

The normal push was rejected by GitHub email privacy protection:
"Your push would publish a private email address" (GH007). Per the user's explicit
instruction, publication work stopped immediately. No retry, force-push, rebase,
reset, history rewrite or account-setting change was performed. No CI run or new
Docker image was triggered, so there is no published image digest or ready image
to report for this attempt. Do not present the intended SHA tag as published.

Current branch: investigate/adaptive-library-updates.
Local HEAD: 8c1f1d1711bdf7e02f3b3f8165c860a84192b0e4.
Last fetched origin: 639355fbad51cd663ab619731453d7b0a08d361c.
The pre-push fetch confirmed the remote was unchanged, with 4 local-only commits,
0 remote-only commits and no divergence. All approved work is preserved:

- b952d64e: pinned official WebUI r3549 integration.
- ec69f7d4: complete optional read-only Komga integration and five review fixes.
- fd8b366f: focused commit of all seven PostgreSQL harness/documentation files.
- 8c1f1d17: packaged Komga GraphQL/WebUI smoke checks and CI formatting gate.

Local syntax, workflow YAML and actual built-WebUI asset extraction checks passed
for the smoke change. Previous fresh validation remains 452 server tests, ktlint,
50 WebUI tests, TypeScript, lint, formatting, production build and all GraphQL
operations; isolated PostgreSQL 14 prepare + 1 restart tests also passed. The new
Linux packaged-image checks have NOT run because the push was rejected.

This rejection/status update is deliberately left uncommitted after stopping.
Next session: review git status/diff and this blocker first. The user must resolve
the GitHub email privacy setting, or explicitly authorize correcting identity on
the unpublished commits under a separately reviewed preservation plan. Do not
assume permission to rewrite those commits. After the blocker is resolved, fetch
again, stop if the remote changed, perform only an authorized normal push, and
monitor the full Linux workflow before reporting a tag/digest or readiness.

M0070 is applied automatically by normal database migration on server startup;
there is no manual SQL step. Komga stays disabled until configured. Production
setup still needs an independently backed-up installation, a readable API-key
secret mounted into Suwayomi, a verified internal API route through its actual
Docker/Gluetun namespace, and optionally a separate browser URL. Installed Komga
version and real NAS connectivity/API behavior remain unverified. No NAS,
production database/downloads/Komga library, extension repository or master branch
was accessed or modified.

The user now explicitly authorizes committing the seven reviewed PostgreSQL
harness/documentation files, extending packaged Komga smoke checks, a normal push
of investigate/adaptive-library-updates and publication by the existing workflow.
Earlier no-commit/no-push restrictions below are historical and superseded for
this publication only. No master merge, extension changes or NAS/production access
is allowed. If origin changes or push is rejected, stop rather than rewrite work.
Starting HEAD: ec69f7d483e1a713eff8bb78dacdd6c4d1878ca9; last fetched origin:
639355fbad51cd663ab619731453d7b0a08d361c. First commit the seven reviewed files as
a focused PostgreSQL-validation commit; keep workflow/smoke changes separate.
Then fetch, verify ancestry and unchanged remote, push normally, and monitor the
Linux server/WebUI/packaged smoke gates before reporting publication readiness.
Record exact source SHA, immutable image tag/digest and run URL here afterwards.
No image is yet declared ready by this new publication task.

Progress: the seven reviewed files were committed as fd8b366f (Add isolated
PostgreSQL migration and persistence validation). Added a separate packaged-image
smoke script and workflow gate covering all eight Komga GraphQL operation names,
disabled/default-user settings, save/Test Connection/disabled search, missing-manga
resolver guards, served JavaScript assets and bundled Komga operations plus r3549
revision. It uses CI's empty loopback container only, without a Komga URL, secret,
source request or production mount. WebUI formatting is now also checked in CI.
Local Python syntax, actual production asset extraction and workflow YAML checks
passed. Fetch confirmed origin still 639355fb, ahead 3/behind 0 before the smoke
commit; no divergence or external branch update. Next: commit smoke changes,
fetch/check again, push normally and monitor the Linux workflow. Exact publication
results and final source/image identifiers must be filled in after CI success.

## Completed: final integration and Git readiness review

User requested a full integration review and fresh server/WebUI validation before
another image. Do not commit, push, merge, publish, deploy, touch the extension
repository or access the NAS. Existing uncommitted PostgreSQL work is preserved.
Fetched the actual remote development branch successfully and independently
checked it with git ls-remote: origin remains 639355fbad51cd663ab619731453d7b0a08d361c.
HEAD remains ec69f7d483e1a713eff8bb78dacdd6c4d1878ca9, with b952d64e (pinned r3549)
as parent. Remote is an ancestor: 2 local-only commits, 0 remote-only, no divergence.
Fetch/read synchronization works; push permissions have not been exercised.
Normal push would trigger the adaptive image publishing workflow; no push is
authorized. No reconciliation merge/rebase/reset is needed with this remote state.

Fresh WebUI tests (50), TypeScript, lint, full formatting and production build
passed (all exit 0, including PWA generation). Every WebUI GraphQL operation also
validated against the exported custom server schema, generating only in memory.
Build warnings: legacy OXC minification unsupported by pinned Vite (legacy plugin
falls back to Terser), plugin timings and >500 kB chunks. No dependency upgrade
was made. Fresh full server validation passed: 452 tests, zero failures/errors/skips,
ktlintCheck passed, BUILD SUCCESSFUL in 4m 24s, exit 0. No production code changed.
Logs: C:/Users/bayleeh/.codex/tmp/suwayomi-postgres/readiness-*
Rechecked existing PostgreSQL evidence: 14 prepare + 1 restart tests, zero failures,
both database snapshots identical across restart, cluster stopped. No rerun is
needed for documentation-only review changes; production source is unchanged.
Source review confirms server service/GraphQL, settings/Test Connection, manual
link/search/unlink, inventory/progress display, cache/manual refresh and backups.
Outstanding: installed Komga version, secret mount, actual Docker/Gluetun route,
real API/disposable packaged-container smoke tests, and real-data restore rehearsal.
The Windows/Java 21 PostgreSQL harness is opt-in and not run by Linux/Java 25 CI.
Current Docker smoke tests cover adaptive/Completed/WebUI, not explicit Komga
operations; add a disposable Komga smoke check when image preparation is approved.
Corrected one stale documentation statement saying PostgreSQL was not tested.
All validation runs completed successfully. No blocking implementation defect was
found. Adaptive scheduling/history/provenance, Completed automation, source-language
filtering, r3549 startup/notifications/navigation and existing tracker code remain
intact. Their dedicated tests passed and relevant source files are unchanged from
the approved r3549 baseline. Komga backup additions are additive. All five review
fixes remain: supported stable URL sorting, encoded separators, atomic restore,
manga/series pagination reset and contradictory-metadata rejection.

Implemented Phase 2 functionality is complete for the approved read-only scope.
No automatic linking, progress synchronization, download-flag synchronization,
metadata/file edits or new source requests were added; those are outside scope.
Exact chapter comparisons depend on explicit book metadata links; filename/title
or book numbers alone remain hints. Large/changing inventories can be incomplete.
No live Komga, Docker image build/run, Linux/Java 25 CI or NAS validation occurred
in this review. PostgreSQL evidence was rechecked, not rerun; harness/source were
unchanged since its passing run. A real-data restore rehearsal remains outstanding.

Final Git state: same branch/HEAD, ahead 2 and behind 0 after fetch. Four modified
files: CODEX_HANDOVER.md, docs/komga-integration.md, server/build.gradle.kts and
ApplicationTest.kt. Three untracked additions: scripts/test-postgres.ps1,
PostgresValidation.kt and PostgresValidationTest.kt. They comprise the PostgreSQL
harness and documentation; no work was stashed, reset, discarded or rewritten.
git diff --check passed. No extension-repository or production-NAS changes.

Safe next steps, requiring the user's subsequent approval:

1. Review the seven uncommitted files, then commit the PostgreSQL harness and
   associated documentation as one focused local commit when explicitly approved.
2. Before any push, fetch and recheck ancestry again. With the current history,
   an ordinary fast-forward push suffices; no merge, rebase or force-push is needed.
   If remote-only commits appear, stop and review them while preserving all work.
3. Obtain explicit push/publication approval together: the branch push triggers
   image publication. During approved image preparation, add/run a disposable
   packaged Komga smoke check before publication and retain migration evidence.
4. Only with separate NAS permission, rehearse real connectivity/API behavior
   using disposable Suwayomi data and a revocable mounted secret, as documented.

Review this section and git status/diff before resuming; do not overwrite work.

## Completed, uncommitted: isolated PostgreSQL validation (19 October 2026)

User authorized an opt-in local PostgreSQL harness, fresh installation and M0069
upgrade checks, preservation/constraints/restart/concurrent restoration tests.
All new changes must remain UNCOMMITTED. No NAS or production access is allowed.
Starting state: clean `investigate/adaptive-library-updates`, HEAD
`ec69f7d483e1a713eff8bb78dacdd6c4d1878ca9`, ahead of origin by 2 commits.
Pinned r3549 integration is parent `b952d64ea5ef403ba17e68f1648a4793d4148cb7`.
Existing tests already accept PostgreSQL Gradle properties, but lack an isolated
provisioning/safety harness. No Docker or PostgreSQL executable is on PATH.
Implemented (uncommitted): scripts/test-postgres.ps1, opt-in Gradle task, test-only
PostgreSQL setup hooks and migration/constraint/persistence tests. Runner creates
its own ACL-restricted loopback cluster, limited role, token-verified databases,
private application paths and a Java 21 non-loopback socket guard.
Run 5 validation: fresh installation, M0069 -> M0070 preservation, constraints,
loopback socket guard and all 13 Komga tests passed (14 tests in prepare phase).
The 20 manual-link/restoration races passed on PostgreSQL; emitted SQL contains
ON CONFLICT DO NOTHING. Both databases' complete sorted table snapshots were
identical across an actual PostgreSQL stop/start. New-JVM restart persistence
checks passed (1 test). Both Gradle phases and the runner exited successfully.
Credential scanning passed; all five disposable clusters (including earlier
attempts) are stopped, with no plaintext credential files remaining.
Full normal H2 suite: 452 tests, zero failures/errors/skips; ktlintFormat and
ktlintCheck passed, BUILD SUCCESSFUL in 5m 5s, exit 0.
Runtime: PostgreSQL 17.11, Java 21, PowerShell 7 on Windows. Binaries are outside
Git at C:/Users/bayleeh/.codex/tmp/suwayomi-postgres/pgsql/bin. Run log:
C:/Users/bayleeh/.codex/tmp/suwayomi-postgres/harness-run5.log; regression log:
C:/Users/bayleeh/.codex/tmp/suwayomi-postgres/server-regression.log.
Run root: C:/Users/bayleeh/AppData/Local/Temp/suwayomi-pg-1d541ddcf13145e3a45c63657c6b88e8
(prepare/restart Gradle logs, per-phase XML reports, snapshots and stopped cluster
will remain for review; plaintext generated credentials are removed on exit).
Issues fixed during harness development: Windows pg_ctl descendant-process wait,
and a new assertion's JDBC t/f boolean representation. No production server
bug or migration change was needed. Earlier failed-run clusters are stopped and
credential files removed. No production/NAS access occurred.
Next steps, in priority order:
1. Review this handover and git status/diff before resuming.
2. User reviews the seven uncommitted files; commit only with explicit approval.
3. To repeat validation, set JAVA_HOME to the Java 21 JDK below and run
   ./scripts/test-postgres.ps1 -PostgresBin C:/Users/bayleeh/.codex/tmp/suwayomi-postgres/pgsql/bin.
Final Git/diff checks passed; no remaining implementation or test blocker. Further
validation is optional and needs separate scope: match actual PostgreSQL major,
real-data restore rehearsal, and NAS-specific connectivity/API behavior. This
harness is Windows/PowerShell 7 + Java 21 only; frozen M0001..M0069 migrations
create its synthetic baseline, not a separate Phase 1 server process.
Uncommitted files: this handover, docs/komga-integration.md, server/build.gradle.kts,
ApplicationTest.kt, scripts/test-postgres.ps1, PostgresValidation.kt and
PostgresValidationTest.kt. Production code and WebUI are unchanged. Review Git
and this section before resuming; do not duplicate or overwrite this work.
## Historical Phase 2 commit validation (before PostgreSQL harness)

User approved the completed Phase 2 work and a local commit on 19 October 2026.
The commit is named `Add optional read-only Komga library integration`; identify
its hash with `git log -1`. It includes the five review fixes and documentation.
No push, merge, publication, NAS access or deployment is authorized. The newer
PostgreSQL results above supersede the PostgreSQL limitations recorded in this
historical section. NAS validation is still a future plan.

Pre-commit review follow-up: user authorized fixing all five review findings while
initially leaving Phase 2 uncommitted for review. Supported `url,asc` sorting replaces unsupported
`id,asc` on GET and POST book-list routes. New pagination validation rejects
contradictory metadata, explicitly unsorted responses, changed page totals/sizes
and duplicate/misassigned books. Encoded URL separators and trailing slashes are
preserved during chapter matching. Backup links now use atomic insert-if-absent,
so concurrent restoration cannot overwrite a manual confirmation. The manga card
resets pagination on manga/series changes and search/dialog state on manga changes.
New contract tests cover 401 scrambled books with repeated chapter-number hints
through both API routes; new URL, concurrent restoration, pagination and UI
regressions cover the remaining fixes. Final follow-up validation is recorded
here: **452 server tests**, zero failures/errors/skips, server ktlintFormat and
ktlintCheck passed together in a successful full Gradle run. **50 WebUI tests**,
TypeScript, lint, whole-WebUI formatting, production Vite/PWA build and
`git diff --check` passed. The concurrent restoration test races 20 fresh manual
confirmations against imports; the emitted H2 SQL was also verified to contain
no matched-row update clause. Exposed insertIgnore is unsupported by H2, so H2
uses an insert-only MERGE, while PostgreSQL uses ON CONFLICT DO NOTHING. A live
PostgreSQL instance was not exercised. No schema, migration, credentials or
packaging change was required by these five fixes. The API still cannot offer
an atomic snapshot during concurrent Komga library edits; detected inconsistent
pagination is rejected. No NAS access, push, merge or deployment occurred.
Phase 2 is based on b952d64e; adaptive/provenance, Completed, language/source settings,
notifications, dependencies and packaging files remain unchanged from that
approved Phase 1 baseline. Generated GraphQL changes remain purely additive.

User approved Phase 2 implementation on 19 October 2026 using mock Komga responses
and disposable data. Work remains on `investigate/adaptive-library-updates`.
Phase 1 is committed locally as `b952d64ea5ef403ba17e68f1648a4793d4148cb7`.
Phase 2 is approved for the local commit described above. No push, image publication, master merge,
extension edits, NAS access or deployment is authorized. This section supersedes
the older planning-only restriction below. No upstream server merge was needed.

Implementation stages completed:

1. Reused existing default-user authentication, settings permissions, Exposed
   migrations, backup models, tracker settings/manga cards and Apollo/test setup.
   M0070 was free and now adds separate connection/link tables. Mounted keys use
   `KOMGA_API_KEY_FILE` (default `/run/secrets/komga_api_key`), never WebUI inputs,
   GraphQL key fields, database credentials, portable backups or HTTP error logs.
2. Added a separate bounded HTTP client, manual title suggestions/confirmed links,
   connection testing, read-only inventory/progress queries, caching and manual
   refresh. Existing source clients, adaptive scheduler, Completed rules, queues,
   downloads, tracker synchronization and reading-progress writes are untouched.
   Komga writes are absent; POST fallback endpoints are documented read searches.
3. Added WebUI tracking settings and manga cards alongside existing providers,
   including Komga-only access without MAL/AniList login. Internal/browser URLs
   are separate; default integration state is disabled. Exact chapter matches
   require unambiguous stored source URL links, with number hints/volumes/specials
   and incomplete inventories explicitly uncertain. Links survive normal database
   persistence; changing API destinations requires reconfirmation. Portable
   backups include non-secret settings/links when user settings are included;
   restore disables the imported connection and requires reconfirmation without
   overwriting an existing confirmed manga link. Sync restore ignores them.
4. Added mock HTTP, schema/permissions, inventory, cache, secret-rotation,
   pagination, backup and WebUI regressions. Regenerated all three GraphQL outputs
   against `server/build/adaptive-schema.graphql` from the actual custom schema.
   New strings are extracted into the English catalogue; other locales fall back
   to English pending translation.

Configuration and behavior: `docs/komga-integration.md`. User's Komga container is
`komga`, mapping `8080:25600` with internal listener 25600, reading existing
Suwayomi downloads. Suwayomi shares another service's network namespace. Exact
Komga version, full namespace owner/network names, firewall/routing, final URLs
and secret mount remain unconfirmed. **No actual NAS connectivity was tested**:
access was explicitly forbidden. Test Connection runs from Suwayomi's own HTTP
client and can establish this during the user's eventual installation review.
Do not assume `http://komga:25600` resolves or routes through Gluetun.

Initial implementation validation before the five follow-up fixes: **444 server
tests**, zero failures/errors/skips (16 initial Komga tests), and server ktlint
passed. **48 WebUI tests** in 12 files passed, as did TypeScript,
lint, whole-WebUI formatting, English extraction, Vite/PWA production build and
`git diff --check`. The final full server test run passed every test; ktlint then
reported one multiline test declaration, corrected by ktlintFormat and followed
by a successful ktlintCheck. No behavioral change followed the passing tests.
The full check used the existing Docker init script, JDK 21, `--max-workers=1`,
`-Dorg.gradle.jvmargs=-Xmx3072m` and Kotlin compilation in process. Local Kotlin
statistics collection adds several minutes after task completion on this host.

Initial real headless Chrome checks against the production build, local mock HTTP Komga
and disposable no-login server passed at 1440x1000 and 390x844: settings, successful
and failed connection tests, provider list, manual linking, URL separation,
inventory/progress, uncertainty, cached failures, refresh and unlink. No uncaught
browser errors or horizontal overflow. A confirmed link and settings survived an
actual server process restart; the three disposable source chapters remained
unread/not downloaded with page zero while Komga reported its separate progress.
Only stored chapter queries were used; no manga-source fetch operations occurred.
Snapshots/reports/logs are outside Git under the existing isolated tooling path.
Temporary QA servers, proxy, mock and headless browsers were stopped afterward.
The existing update notice was ignored without downloading an update. Browser QA
found an idle pooled-socket failure; bounded read-only connection recovery fixed
it and is covered by a dropped-connection regression. A nested translation that
hid the uncertainty label was also corrected before the passing final UI checks.

No live Komga, NAS connectivity, Linux/Java 25 CI, Docker image build or live
provider authorization was tested. Existing legacy-minifier/chunk-size and
Kotlin/Gradle deprecation warnings remain. Numeric-only book metadata cannot
prove chapter identity, large inventories may be truncated, and untranslated
locales use English fallback. Full configuration and bounds are documented.

The upstream WebUI baseline remains pinned r3549 (`8ab2f3385d643f36a415bc8df0c414f86c8a97b1`),
with r3518 (`41443eaba44bcbdf45d666fe27471b50d41fd994`) as original ancestor.
The server base remains custom r2422; this phase adds only the approved custom
Komga integration and backup/schema/UI hooks, with no new dependencies or Docker
packaging changes. Local isolated tooling remains the Node 24/JDK 21 setup below.

## Approved and committed Phase 1: bundled WebUI r3549 integration

User approved the completed Phase 1 integration, validation results and a focused
local commit on 19 October 2026. Phase 2 was subsequently authorized as recorded
above. Stay on `investigate/adaptive-library-updates`; no pushing, image
publication, upstream server merging, NAS deployment or extension edits are
authorized. Historical branch/release statements below are retained for context.

The supplied checkout is `C:\Users\bayleeh\Documents\Suwayomi-Server-Personal`;
the previously documented `D:\Git` location is unavailable on this machine.
Starting HEAD and remote branch: `639355fbad51cd663ab619731453d7b0a08d361c`.
Its successful publication was run 39068902209, SHA tag `sha-639355fbad51`, digest
`sha256:d6746bb348b065c57b5a3842686a9ab3edcd2c305274beae66115932f76b95de`.
This is the preceding published image, not the new local integration.

The original bundled ancestor remains official r3518,
`41443eaba44bcbdf45d666fe27471b50d41fd994`. The new integrated baseline is pinned
r3549, `8ab2f3385d643f36a415bc8df0c414f86c8a97b1`. See
`docs/webui-r3549-integration.md` and `webui/PERSONAL_BUILD.md` for the comparison,
custom conflict resolutions, lifecycle fixes and unchanged server/packaging scope.
The custom server remains based on upstream r2422 (`d4f88a34`); upstream's only
subsequent changes at investigation time were two README edits. No new SQL migration.

Local validation completed: **428 server tests**, zero failures/errors/skips,
including all ten Completed-category tests; server ktlint passed. **39 WebUI
tests** in ten files passed (18 added regressions), as did TypeScript, lint,
full WebUI formatting, English catalogue extraction, production Vite/PWA build
and `git diff --check`. All three GraphQL outputs were regenerated from
`server/build/adaptive-schema.graphql`, exported by the actual custom-server
GraphQL regression test. Only operation types changed; schema/helper output
remained identical. Custom operations and diagnostic fields remain present.

Real headless Chrome checks against the built production UI and a fresh local
no-login server passed at 1440x1000 and 390x844: direct startup, settings navigation,
custom adaptive/Completed controls, source settings and tracker settings, no
horizontal overflow or uncaught browser errors. A forced TCP disconnect recovered
all four permanent subscriptions and received new data. Notification enable/disable
and an update of the verified empty test library used stored chapter queries only;
no manga source requests were needed. Screenshots and JSON reports are outside the
checkout at `C:\Users\bayleeh\.codex\tmp\suwayomi-r3549`. The temporary QA proxy
was corrected to close both socket ends before judging reconnection behavior.
The temporary QA server, build proxy and headless browsers were stopped afterward.

Local tooling: isolated Node 24.22.0/pnpm 11.1.2 and JDK 21.0.12.1+1 under that
temporary directory. The default Kotlin compiler heap was insufficient; the
successful full check used `-Dorg.gradle.jvmargs=-Xmx3072m`,
`-Pkotlin.compiler.execution.strategy=in-process` and `--max-workers=1` with the
existing Docker Gradle init script. No application/build-source change was needed.

The approved Phase 1 integration is recorded by local commit
`b952d64ea5ef403ba17e68f1648a4793d4148cb7`, preceding the Phase 2 commit above.
No push or publication is authorized. No Docker image build,
Linux/Java 25 CI run, NAS deployment, production-library test or live tracker
authorization was performed. Migration navigation/provider behavior is covered by
fixtures; no real source migration or reading/download transfer was attempted.
The existing build emits legacy-minifier fallback and chunk-size warnings but
completes successfully. The existing update checker can offer an official preview
on a custom server; QA ignored that notice without downloading an update.

## Current release: historical diagnostics and date provenance

User approved implementation, final review and push/publication on 17 October 2026.
This commit contains the reviewed changes; the existing branch workflow builds the
new image with a `sha-<first 12 commit characters>` tag. Verify its completed CI run
and registry digest before using it. No NAS deployment or master merge is authorised.
This section supersedes older implementation/publication status further below.
The preceding published revision was `f00743015bc6298606c7e8095f943da0e39ae914`
(`sha-f00743015bc6`, digest
`sha256:3d4ce89e352a551f05dd60647bfde636c774aa03aba307e932f48bca2a72cb4c`).

- Historical assessments now persist one reason/count/time summary in adaptive
  state JSON and expose it through GraphQL and the bundled WebUI. Messages distinguish
  missing/unknown/fallback dates, duplicate conflicts, backwards dates, bulk uploads,
  insufficient events, unsupported/inconsistent intervals, stale history, suppression,
  contradictions, expiry, provisional hints and active schedules. Fresh observations
  and provisional confirmations remain separate. Legacy state without an assessment
  explicitly says it has not been assessed since this update.
- New `HistoricalEvidence.kt` classifies extension output before server fallback
  substitution. A bounded ledger (at most 96 URL/date SHA-256 fingerprints and origin
  enums, no plaintext URLs or chapter lists) preserves evidence origins across refreshes
  and restarts. It retains the newest chapter numbers; eviction loses proof safely.
  Unchanged legacy dates remain UNKNOWN and known fallback dates remain FALLBACK,
  including date matches on changed URLs. Dates missing in output are assessed as
  missing; newly generated substitutions are recorded as fallback. Retaining an
  existing DB date preserves its known origin (otherwise unknown).
- This is server-boundary provenance, not proof of fresh parsing or date precision.
  Comix accepts stored chapter dates and may preserve them, merge stored chapters, or
  return the supplied list unchanged. It parses relative site dates. Existing records
  cannot be retroactively certified. New/changed values not matching stored evidence and newer than the retained
  evidence horizon are called EXTENSION_REPORTED, never "freshly parsed". No extension files changed.
- Only historical seeding/revalidation uses these origin restrictions. Four fresh
  observations and discovery-time learning retain their existing rules. Six reliable
  historical events, two genuinely new confirmations, grouping/recency thresholds,
  manual refreshes, guard checks, controls and 14-day backoff are unchanged. No new
  source calls, jobs or logging were introduced; Comix protections remain untouched.
- Additive defaulted JSON fields need no database migration. Normal DB backups include
  diagnostics/ledger. Portable chapter restoration and reset clear provenance and old
  assessments, retain existing history suppression/watermark semantics, and explicitly
  report suppression without claiming a source assessment. Restored dates are unknown
  on later refresh. State expiry retains bounded origins but expires the old hint.
- Main files: adaptive `HistoricalEvidence.kt`, `AdaptiveSchedule.kt`,
  `AdaptiveUpdates.kt`; `Chapter.kt` passes the actual persisted dates alongside raw
  output; GraphQL `AdaptiveUpdateType.kt`; WebUI `AdaptiveMangaSchedule.tsx`, adaptive
  fragment/generated types, English catalogue; targeted evidence/refresh/persistence/
  GraphQL/UI tests. Further details in `docs/adaptive-library-updates.md`.
- No live NAS/Comix chapter evidence for The Extra's Academy Survival Guide was captured.
  Its exact rejection cause remains unverified until this diagnostic build passes CI,
  is published, and is run against that title. Do not relax thresholds to force
  qualification. Source migration and Completed-category behaviour are unchanged.

Focused final review added a monotonic `dateEvidenceThrough` timestamp to the same
JSON state. A deleted/evicted chapter must not return as newly sourced proof just
because its fingerprint disappeared: unmatched dates at or before this horizon stay
UNKNOWN. It captures successful-check time and the largest observed date (including
future/invalid dates), survives resets and expiry, and uses legacy lastCheck as an
initial lower bound. This also protects previously future-dated unknown values when
wall time catches up. Missing provenance can lose specificity (fallback becomes
unknown after deletion), but cannot gain trust. An erroneous far-future source date
can conservatively block historical shortcuts until fresh learning establishes a
schedule; normal four-observation learning is independent. Tests cover deletion,
changed-URL reuploads, reordering, eviction, restart/reset/expiry, two provenance-
checked new confirmations, repeated lists, and unchanged per-refresh source calls.
The unknown-date UI explanation and timestamp-count label were clarified accordingly.
Completed-category implementation/hooks and Comix extension/protections are unchanged.

Final local validation: **428 server tests**, zero failures/errors/skips; server
ktlint passed. **21 WebUI tests**, TypeScript, lint, changed-file formatting and Vite
production build passed. Generated GraphQL types came from the tested server schema.
Regression coverage includes unknown legacy dates, weekly-looking reused fallbacks,
changed URLs, omitted dates retaining their original origin, provisional revalidation,
independent four-observation learning, bounded evidence, reset/restore, persistence
round-trips and one source call per manual refresh. The final focused review and
full rerun passed: 428 server tests (including all 10 Completed-category server tests),
ktlint, 21 UI tests, TypeScript, lint, formatting and production build. No live
source/NAS validation. Publication is now authorised through the existing workflow;
NAS deployment remains the user's responsibility.


## Workspace and Git

- Canonical repository: `D:\Git\Suwayomi-Server-Personal`, origin https://github.com/Chronosmage-alt/Suwayomi-Server-Personal.
- Active branch: `investigate/adaptive-library-updates`, HEAD `0a64c6b103fcba494668076d7af9d5e6c9bbf2ca`, pushed to origin. Working tree was clean before this document.
- Feature commits: `a5f600c2` server/adaptive work; `aeeb832c` bundled UI and Docker workflow; `10d8ed2d` source-language filtering/tests; `0a64c6b1` mobile toggle spacing.
- Local `master`: `d4f88a34`, reported two commits behind origin/master. GitHub reported the feature branch four commits ahead/two behind upstream Suwayomi master. Those two upstream commits have NOT been reviewed or merged; remote counts can change.
- Canonical UI is now **`webui/` in this server repository**, imported from official r3518 source commit `41443eaba44bcbdf45d666fe27471b50d41fd994`, with custom changes. Do not substitute current upstream WebUI blindly.
- Old separate repository `D:\Git\Suwayomi-WebUI-Personal` remains on `investigate/adaptive-library-updates` at `41443eab`, with uncommitted earlier adaptive changes. It is a historical working copy, not the build source. Do not discard its changes.
- `D:\Git\Extension-selfhost` is a separate repository. Do not modify it; recheck its Git state and `source/UPSTREAM.md` before any future extension work.

## Implemented behavior and design

- Server decides automatic eligibility from persisted local state before invoking extensions; no network request is needed to decide whether a title is due.
- Global switch defaults OFF; shared per-source switches use stable source IDs plus provider packages; per-title overrides are FOLLOW_SOURCE, SMART and NORMAL. Source preferences survive removal/reinstall when identity matches.
- Four qualifying release observations establish a consistent cadence. **Existing historical chapters are only a baseline: v1 does not seed learning from old release dates.** Normal updates continue during learning.
- New forward chapter numbers (one to three distinct numbers per candidate batch) can count as one observation. Initial imports, duplicates, backfills and ambiguous larger batches cannot fabricate a pattern. Raw publication dates are distinguished from discovery times; discovery-only learning requires a previous check within one day.
- Uses the latest four qualifying observations to assess intervals, retaining bounded history. Early-release guards use half the learned interval, capped at seven days. Daily/frequent titles retain normal checking.
- Faster releases or repeated cadence conflicts return the title to normal checking while relearning. Manual refresh observations can reveal changes between guards.
- After missed releases and a 48-hour grace period, backoff increases through 1/2/4/7/**14 days maximum**. This caps eligibility spacing, not actual execution during downtime, exclusions, queues or source failures.
- Manual library/category/title refresh bypasses adaptive timing; initial fetches run normally. Existing extension protections still apply. Skips preserve chapters/metadata.
- Existing eligibility filters remain authoritative, including Completed exclusion regardless of adaptive settings. Direct manual refresh remains available. Disabled adaptive mode preserves original updater request behavior.
- Migration M0068 adds source-policy and manga-state tables; global switch is server configuration. Learning survives restarts. Portable backup controls follow applicable backup flags; chapter restoration resets derived learning safely.
- Concurrency fixes preserve queued manual work, separate active request IDs from latest title status, serialize notifications, track category preparation, and prevent older jobs overwriting newer statuses. Starting another update does not implicitly cancel current work; explicit Stop still cancels.
- Reset/restore uses the shared per-title lock. No cross-source download coordination or server-level bridge to extension cooldown deadlines exists: current source API does not expose those deadlines.
- Preserve all custom Comix pacing, serialization, retries and persisted cooldowns. Scheduling complements them; it cannot guarantee protection from IP bans.

## Main changed files / navigation

Paths below are relative to repository root; the imported `webui/` contains the full frontend, not only feature files.

- `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/update/adaptive/{AdaptiveSchedule,AdaptiveUpdates}.kt`: pure learning/scheduling and persistence integration.
- Same Kotlin root: `manga/impl/update/{Updater,UpdateJob,UpdateStatus}.kt`, `manga/impl/{Manga,Chapter}.kt`: eligibility, job lifecycle, locking and observations.
- Same root: `server/database/migration/M0068_AdaptiveUpdates.kt`, `manga/model/table/AdaptiveTables.kt`; server configuration in `server/server-config/src/main/kotlin/suwayomi/tachidesk/server/ServerConfig.kt`.
- Same root: `graphql/{queries/AdaptiveUpdateQuery,mutations/AdaptiveUpdateMutation,types/AdaptiveUpdateType,types/UpdateType,server/TachideskGraphQLSchema}.kt`.
- Same root: `manga/impl/backup/proto/` export/import, models and manga handler: backup compatibility/reset behavior.
- `webui/src/features/settings/components/globalUpdate/AdaptiveUpdateSettings.tsx` and its `.test.tsx`; settings integration, manga details `AdaptiveMangaSchedule.tsx`, updates `SkippedUpdateDialog.tsx`/`UpdateChecker.tsx`.
- `webui/src/lib/graphql/adaptive/AdaptiveUpdates.ts`, source/updater/settings fragments and generated GraphQL files; backup UI integration; `webui/src/i18n/locales/en.po`.
- Language fix reuses normal Browse `Sources.filter`, enabled-language/source and NSFW preferences; groups by provider extension, displays language, offers Show all languages OFF by default. Filtering writes no preferences and changes no scheduling logic. Hidden saved settings survive; dormant preferences can be revealed with Show all.
- `webui/{package.json,pnpm-lock.yaml,vitest.config.ts,tsconfig.node.json}`: meaningful UI test runner/configuration.
- `.github/workflows/adaptive-container.yml`, `docker/adaptive/` (Dockerfile, startup, Compose, build init script, README), `docs/adaptive-library-updates.md`.
- For exact inventories use `git show --stat <commit>`; avoid confusing the complete UI import with unrelated feature edits.

## Validation and known limits

- Full server suite previously passed **386 tests**, zero failures/errors, plus ktlint; final Linux image workflow reran server tests/ktlint/build successfully.
- Six UI tests pass: language filtering/grouping, Show all/hidden retention, stable large string IDs/provider identity, changed language preferences, source/NSFW filters and empty/read-only cases.
- TypeScript (`pnpm tsc`), lint, changed-file formatting and production Vite build pass. CI runs UI tests before publishing.
- Isolated browser checks passed French-only/default and Show all behavior, saved-preference retention with zero adaptive mutation writes, desktop/mobile layout, no horizontal overflow and no browser errors.
- Earlier isolated H2 checks covered process-restart persistence, backup/reset, concurrency, notifications, manual/automatic behavior, disabled parity and Completed exclusion. Fixtures model parser shapes; they are not live source captures.
- PostgreSQL, production-size NAS database migration, real source timing/rate-limit behavior and long-running release learning remain unverified. User reports successfully deploying the test image on the NAS; that is not independent end-to-end NAS validation by Codex.
- Existing technical document has historical statements about the separate UI workspace/no UI unit suite: this handover supersedes those statements.

## Docker build/publication and NAS

- User has UGREEN **DXP4800 Pro (amd64)**, single-user Suwayomi with no login; preserve existing permissions/account architecture. Test folder: `Shared folder/Vol2Apps/SuwaDevTest` (do not guess its absolute Linux mount path).
- GitHub builds both server and bundled custom UI from this branch; Java 25, Node 24, pnpm 11.1.2, Ubuntu runner/Xvfb. It resolves official preview runtime to a digest, builds linux/amd64, smoke-tests GraphQL and bundled UI, then publishes GHCR. No NAS deployment occurs in workflow.
- Latest successful run: https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39039238744.
- Published image: `ghcr.io/chronosmage-alt/suwayomi-server-personal:adaptive-test`; exact source tag `sha-0a64c6b103fc`.
- Image digest: `sha256:8999bfa462cf42de4b926907396b8bf58e64d6eb53a1bc30aff909e83dddaf97`.
- Pushes to the feature branch matching workflow paths trigger publication. Manual dispatch is configured but needs workflow on the default branch for normal GitHub availability. Docs-only handover changes do not match image trigger paths.
- Template: `docker/adaptive/compose.yaml`; full setup/migration/rollback: `docker/adaptive/README.md`. Uses separate `./data`, H2, host port 4568, CUSTOM UI, UI auto-update disabled. Startup installs bundled UI. Verify NAS UID/GID and actual database type.
- Template `UPDATE_INTERVAL=0` disables automatic checks at startup; change to desired hours (e.g. 12), recreate container, enable adaptive global/source controls to test scheduling.
- To update an existing test Compose using adaptive-test: `docker compose pull` then `docker compose up -d`; refresh browser. Codex has no NAS access.
- Keep production Suwayomi and Komga mounts/ports separate. For migration tests copy a consistently stopped/offline database into test data, never share a live DB. Both instances still share public IP/source limits.
- Before migration retain a complete data/config backup. Rollback requires the old image **and its matching pre-migration data**, not an old image against a migrated DB. Pin image digest for reproducibility; rerunning one source SHA can resolve a newer official preview runtime.

## Outstanding decisions / next steps

1. Review the two upstream commits and compatibility before proposing an integration; none merged yet.
2. User asked about historical-date learning. Discussed provisional history-based hints confirmed by new observations; **not implemented or approved as a concrete change**. User uses deduplication, but that does not validate upload dates/backfills.
3. User asked to automate Completed category assignment from source status. Proposed optional category rule, retaining other categories and separating category assignment from stopping updates. **Not implemented**; confirm intended category/rules first. Source Completed may precede final translated uploads; excluded titles cannot discover status changes through skipped updates without a separate/manual metadata check.
4. Continue isolated NAS migration/behavior testing; collect results before claiming production readiness.

## Working constraints and local tooling

- Do not change the extension repository, merge, deploy to NAS or alter production data without separate authorization. User authorized the completed language-fix commits/push/image publication; do not assume blanket approval for future features. Current request authorizes this handover document only, no application changes.
- Read applicable AGENTS.md and contributing/build instructions before new work; no AGENTS.md was found in repository file inventory during this task.
- Local tools are under `D:\Git\.adaptive-tools`: Node `node-v24.21.0-win-x64`, pnpm in `node_modules\.bin`, Java `jdk-21.0.12.1+1`; CI uses Java 25. Use `pnpm.cmd` in PowerShell (execution policy blocks .ps1). Corporate/system certificates required `NODE_OPTIONS=--use-system-ca` for dependency installation and Java Windows-ROOT trust settings locally.
- No local Docker daemon; Linux container verification is CI. Logs/browser fixtures/screenshots live under `.adaptive-tools`, outside tracked source. Temporary local QA server/proxy were stopped. Never print Git credential-manager tokens.

## Local continuation: historical learning (pending user review)

The approved provisional-history feature is now implemented locally, without
commits, pushes, image publication or NAS deployment. See the new provisional
historical learning section in `docs/adaptive-library-updates.md` for classifier
thresholds, confirmation, suppression and compatibility rules. This supersedes the
earlier statement that v1 cannot seed historical hints: hints now require six
historical events and two subsequent confirmations before checks are reduced.
Validation results are reported in the accompanying conversation.

Historical-learning validation: 392 server tests passed (zero failures/errors/skips)
and ktlint passed; eight WebUI tests, TypeScript, lint, changed-file formatting and
production build passed. No live-source, new browser visual or NAS validation.

Focused historical-learning review fixed revalidation of raw history during
confirmation, monotonic rejection watermarks (including stale-state expiry), and
reset/restore suppression for titles without an adaptive row. Added regressions
cover imports/repeated refreshes, seed-versus-confirmation separation, unreliable
dates and independent fresh learning, persisted-state reload, manual request counts
and backup restoration. Full server suite: 401 passed, ktlint passed. WebUI: eight
tests, TypeScript and lint passed again. No process/NAS restart test in this review.
No commits, pushes, publication or deployment. Completed-category investigation is
next; no category implementation is approved yet.

## Local continuation: Completed-category automation (pending review)

Implemented the approved add-once rule locally. See
`docs/completed-category-automation.md` for design, backup flags and limitations.
Verified the actual no-login path: AuthMode.NONE maps to Admin(1), existing GraphQL
wiring injects that ID. No authentication/user-management files were modified and
no account selector was added. Settings use a transactional per-user rule table,
rather than extending UserConfig, to serialize configuration/assignment/removal.
Migration M0069 adds rule and handled tables. Metadata success with COMPLETED
triggers add-only assignment for existing library membership. Manual removal via
existing GraphQL/CategoryManga APIs persists suppression. Organise previews/apply
use stored status only. Deleted destinations remain inactive with a warning.
Portable backups use Include user settings, categories and manga, remapping category
orders and restoring handled records before activating the imported rule. Older
backups and sync restores preserve existing local controls. A portable library
backup only carries handled records for included manga; full DB backups carry all.
WebUI Library settings shows switch, category names and Organise preview. Existing
category update/download rules still apply; no direct chapter/progress/queue/file
changes. No source request/job was added.
All changes remain local; no commits, pushes, publication or deployment.

Completed-category final validation: full server suite 410 tests, zero failures,
errors or skips; ktlint passed. WebUI 13 tests, TypeScript, lint, changed-file
formatting and production build passed. The no-login/default-user GraphQL test is
explicitly discovered and passed. Internal backup category clears bypass manual
opt-out recording; rule activation follows handled-record restoration. Same-page
category edits refresh the settings warning without polling. No live-source,
PostgreSQL, NAS or new process-restart validation in this implementation.

## Final combined review, 17 October 2026 (still local/uncommitted)

Reviewed historical learning, Completed categorisation, M0068/M0069, persistence,
backup flags/remapping and current source-migration paths. Fixed a reset edge case:
reset/restore now keeps the known rejection horizon while clearing schedule and
observations, preventing truncated responses reviving old candidates. Added reset
regression and GraphQL tests for removal/clear before first handling and deleted
category warnings. No migration behaviour was implemented or changed.

Full server suite 412 passed, zero failures/errors/skips; ktlint passed. WebUI 13
tests, TypeScript, lint, changed-file formatting and production build passed. No
new NAS/PostgreSQL/live-source/process-restart validation. Ready for user review
and subsequent isolated Docker/NAS testing, not a production validation claim.

Read `docs/source-migration-review.md` for investigation. Current migration reuses
or creates a separate destination record by source ID/URL; does not transfer title
mode or adaptive history. Existing destination modes/state are not explicitly
replaced, but ordinary destination fetches can update learning. Category copying
adds old categories to existing destination categories; Completed handled records
remain per manga ID. A new destination does not inherit manual opt-outs. Optional
old-category cleanup currently records suppression on the old record via the
shared GraphQL clear path. Reading-state/files behaviour is pre-existing migration
logic described in the review. User preference for copying explicit modes only to
truly new destinations remains a future, separately approved migration change.
No extension changes, commits, pushes, publication or deployment.

## Publication authorization, 17 October 2026

User approved committing and pushing the reviewed historical-learning and Completed
category changes on investigate/adaptive-library-updates, then publishing through
the existing adaptive-container workflow. Earlier local-only status notes above
are historical. Container smoke checks now assert the new GraphQL controls and
bundled WebUI operations. No master merge, extension changes or NAS deployment is
authorized. Exact resulting SHA/tag/digest and CI outcome are reported separately.
