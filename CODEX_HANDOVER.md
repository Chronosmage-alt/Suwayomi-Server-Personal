# Suwayomi adaptive updates — handover

Updated 30 October 2026. This document is context for a fresh Codex conversation.

## Bundled WebUI r3562 — 30 October 2026

The user accepted the recommended WebUI integration. Official base r3553
`c3465cb4ea15a8778fa6ded50e68bddd18052145` advances nine commits to r3562
`7d9f8ee679ed0ce5683ea570e3845798bee7d6de`. Details are in
`docs/webui-r3562-integration.md`. Preserve the personal startup generation,
cancellation/retry and notifier safeguards. Excluded scanlators now use the new
`GET_MANGAS_META` batch query, not the broad library-card query from upstream.
GraphQL codegen used the existing personal `server/build/adaptive-schema.graphql`;
only operation types changed. No server runtime or dependency changes were made.
The custom CI revision and smoke expectations use r3562. All 91 frontend tests,
TypeScript, lint, formatting, English extraction and production Vite/PWA build
passed locally. Temporary artifacts are in the `suwayomi-webui-20261030` directory
under the existing Codex tmp root. Desktop/mobile browser checks also passed for
chapter progress and custom library settings with no overflow or browser/GraphQL
errors. The separate fixture runtime and proxy were stopped. Changes remain local;
no image publication or NAS deployment was performed.

## Library performance and single-owner runtime — 22 October 2026

The user explicitly requested performance work and removal of multi-user behavior.
They clarified slow loading occurs when opening the library in any category and
that their setup currently has no login. Keep no-login access and existing owner 1;
do not delete data/user foreign keys or require a new login. No NAS access/deployment
has been performed. Details are in `docs/library-performance-single-owner.md`.

The category query now selects chapter-summary dates only for the active sort,
omits unused latest-read data and only finds first-unread targets when continue
reading buttons are enabled. Highest chapter numbers remain available for migration;
badges, duplicate/tracker filters and search metadata remain. Full manga details
retain their original fragment. The initial download queue loads in the background.
Sort updates avoid deep manga comparisons and quadratic card lookups, update live
non-random ordering correctly and preserve random ordering during data refresh.

The public personal schema exposes no user listing/lookup, invitations/registration,
role administration or secondary-user recovery/password mutations. Owner-only login
and refresh remain for optional protection; no-login mode is unchanged. HTTP/WS
token resolution rejects secondary identities. Owner permission checks bypass role
queries. Sync startup subscribes only to owner 1 without a new-user discovery timer.
Historical account/storage tests deliberately use an internal compatibility schema;
the actual production schema/session rules have separate OwnerAccountTest coverage.
No database migrations or deletions were introduced. Account-related generated UI
types were removed by real schema regeneration, not hand edits.

Isolated tools remain under the `suwayomi-r3549` temporary directory. New performance
scripts, raw measurements, seeded H2 data/runtime copies, browser artifacts and logs
are under `C:/Users/bayleeh/.codex/tmp/suwayomi-performance-20261022`.
The fixture has 300 manga/150,000 chapters in three categories. Alternating old/new
queries, excluding two warm-up runs each, measured median 732 ms vs 258 ms with
continue-reading data retained on both sides; core results were compared exactly.
These are synthetic API timings under shared machine load, not measured NAS gains.
Full server validation passed 480 tests, ktlint and shadowJar; final frontend tests
passed 74, with TypeScript, lint and formatting passing. Desktop/mobile category and
all date-sort navigation passed without page/GraphQL errors or overflow; covers were
fixture placeholders. Packaged smoke validates the removed account API as well as
Komga/default-user operations and assets. Local QA dismisses the unrelated official
server-update prompt; it never downloads an official replacement.

Final local verification: 480 server tests passed against the personal schema
by default (only four historical account classes opt into compatibility), zero
failures/errors/skips; server ktlint and JAR build passed. All 74 frontend tests,
TypeScript, lint, formatting, schema generation, English extraction and production
Vite/PWA build passed. Both continue-reading settings and all date-sort modes
passed in desktop/mobile browser checks. Quantitative claims are limited to the
alternating synthetic API benchmark; browser timings were noisy and are not a
measured overall/NAS speedup. All task-owned QA/browser processes were stopped.

Implementation was committed and pushed as `dcdc5d4bba9d23542db2b8ffb60b8045033573fa`.
Actions run 39755210388 succeeded, including Linux/Java 25, WebUI and container
smoke checks, then published `adaptive-test` and `sha-dcdc5d4bba9d`. Both were
registry-verified at
`sha256:2b4059449eb977e4b4b956c00f739e2af0a928039cccb8f19f0650de1c917d29`
with the implementation SHA in their OCI revision label. No NAS deployment or
production-library performance measurement was performed.

## Current upstream integration — 22 October 2026

The user approved bringing in the reviewed server and bundled WebUI updates,
including the proposed tested-image publication. Broader slow-loading/performance
investigation is explicitly deferred until after these fixes. NAS deployment is
not included. See `docs/upstream-20261022-integration.md` for exact pins and scope.

Server upstream target is `6b481e8b62938b9c060eb210b934e0fbfdfa466b` (13 commits
after base `d4f88a34`); WebUI target is r3553
`c3465cb4ea15a8778fa6ded50e68bddd18052145` (four commits after r3549).
Preserve personal adaptive/history, Completed and Komga features. The updater's
sole merge conflict retains the personal implementation with the upstream
positive-last-read timestamp predicate. Imported updater tests must mock all FIVE
arguments to the Boolean-returning method and account for personal skipped history.

Local tooling remains under `C:/Users/bayleeh/.codex/tmp/suwayomi-r3549`.
Quote the entire `-Pkotlin.compiler.execution.strategy=in-process` argument in
PowerShell; otherwise it splits at the dot. New validation logs use `upstream-*`.
Browser reports, screenshots and an isolated runtime/data copy are under
`C:/Users/bayleeh/.codex/tmp/suwayomi-upstream-20261022`. Never rebuild/overwrite a
JAR while a QA server runs from it; use a runtime copy. QA uses loopback only,
disabled CEF and a known missing temporary Komga key path, with no live credentials.

Local final validation passed: all 478 server tests (zero failures/errors/skips),
ktlint and shadow JAR; all 66 WebUI tests, custom-schema codegen, TypeScript, lint,
formatting, English extraction and production/PWA build. Browser checks passed at
desktop/mobile sizes without page/GraphQL errors or overflow. Local packaged smoke
checks passed including r3553, new DB settings and Komga operations. Build retains
the pre-existing legacy minifier fallback and large-chunk warnings. Logs are
`upstream-validation.log` and `upstream-webui-build.log` in the toolchain directory.

Integration was committed/pushed on dev as merge commit
`9502680fbe9d1f96a3517944df1da516fd3f117f`, retaining upstream server ancestry.
Actions run 39744851195 succeeded including Linux/Java 25 checks and container
smoke tests, and published `adaptive-test` plus `sha-9502680fbe9d`. Registry-verified
digest for both is
`sha256:21b56e956f1825cd3630da52b3dbeff0075b165947b3e49d9458a3ddb6d60d9b`,
with OCI revision equal to the integration SHA. All task-owned local QA processes
were stopped; checkout was clean after push. No NAS deployment occurred. The
reported slow loading remains the next, separate investigation.

## Latest matching policy — explicit personal-library override

The user supplied a completed scan: 300 indexed series, 309/309 processed,
4 auto-linked, 36 already linked, 180 ambiguous/review, 0 opted-out, 89 unmatched.
Absolute Regression has Suwayomi author U+200B + Jang Yeonghun and artist
U+200B U+200B + Studio JHS; Komga credits JP, Jang Yeonghun (장영훈), and
Park Jin-Hwan (박진환). This confirms creator-credit comparison rejected it.
The user then explicitly requested that an exact name be sufficient for their
personal setup. This supersedes the stricter primary-title creator/URL rule in
older sections: a UNIQUE normalized primary-title match now links regardless of
creator or URL differences. Multiple primary/alternate candidates still require
review; alternate-title-only matches keep corroboration and conflict checks.
Preserve manual decisions/opt-outs, index validity/freshness/completeness, all
caps/caching/batching and source-free operation. No fuzzy matching, migrations,
backup changes, source metadata fetches, or NAS writes are needed. A regression
uses the actual Absolute Regression credits and verifies alias collisions still
block linking. The 89 unmatched titles may still require manual links; don't
promise that every reviewed title becomes linked (duplicate candidates remain).
After installing the new image, run Scan for matching Komga series again to
apply the new policy to existing unlinked library entries using the shared index.
Local validation: all 44 targeted Komga/migration/GraphQL/custom-updater tests
passed with zero failures/errors/skips; ktlint passed. CI will run the full server
and WebUI suites before publishing the new matching-policy image.

## Latest follow-up: Docker publication and missing Komga labels

Additional user-provided log contained MAL tracker HTTP 401s, followed by a
successful OAuth token/account response (200); no Komga error was present.
Do not change tracker code or claim the manga refresh succeeded from that alone.
A separate confirmed error was checkForWebUIUpdate trying to read CUSTOM's
relative version-mapping URL. This follow-up returns the installed version and
false for CUSTOM without network/release-mapping lookup; standard flavors retain
the existing path. Regression covers raiseError=true, and packaged smoke checks
the real GraphQL query reports r3549 with updateAvailable=false. User NAS and
credentials remain untouched. Label correction itself was pushed as 71039868;
its image precedes this additional updater correction.

The user authorized publishing latest dev and deleting the old investigation
branch only after successful publication. PR #1 had merged dev INTO
investigate/adaptive-library-updates, not the reverse; its tree equalled
63519fcf, so no merge/rewrite was needed. A workflow-comment trigger commit
60c01afd built the correct source but failed the packaged smoke test because
its exact KomgaSettings field assertion predated autoLink/showCounter.
Commit 9e8cc37e fixed smoke checks and published successfully in Actions run
39287177128. Both adaptive-test and sha-9e8cc37e988b were registry-verified at
sha256:2f8fa850362aa45a51684c5ebceb3ec674a489710bc75c7410ca0f7de693cfbb,
with OCI revision 9e8cc37e988bf24c94ba08e529babb66b9b1fc05. The old remote
investigation branch was then deleted with explicit user authorization.
No NAS deployment or extension-repository change occurred.

The user subsequently reported hashed labels for the two new switches and no
readable scan button. Root cause: newly introduced Lingui strings had never
been extracted into the English PO catalogue; development macro fallback hid
this in earlier tests. This follow-up adds all missing English messages, real
compiled-catalogue regressions (66 UI tests total), PO import typings and the
Lingui Vite plugin in tests. CI now extracts English and rejects catalogue drift
before building. All 66 UI tests, TypeScript, lint, formatting and the local
production/PWA build passed. The correction does not change matching or application logic.
The fresh image for this follow-up must be verified from its new Actions run;
the 9e8cc37e image above predates the label correction.

User also showed manual search finding Absolute Regression, and says automatic
linking is enabled and it was never unlinked. No completed scan result or source
metadata has yet been supplied; do not claim a matching defect or loosen safety
rules from the title screenshot alone. The manual scan button is below the two
switches/help and before Browser-facing Komga URL, but its label is hashed in
the affected image. Save preferences first, then run that action and inspect
counts. Manual search suggestions require confirmation. No live NAS inspection
is authorized or has been performed.

## Komga automatic linking and blue counters — implementation / final validation

Updated 19 October 2026. This section supersedes the investigation-only approval
wait below: the user explicitly approved implementation on dev. No commit, push,
publication, deployment, master merge, extension/Comix change or NAS access is
authorized. Preserve every uncommitted change. Do not reset, clean or discard work.
HEAD remains 1e478517ad7288e1fe433c1899505db393633482 on dev tracking origin/dev.
The original uncommitted handover from the earlier request remains preserved below.
No applicable AGENTS.md was found in the repository/ancestors; none were changed.

The user reports working Komga on UGREEN, on komga_default plus manga_default,
reachable by Suwayomi through Gluetun. No NAS access/network changes were needed
or performed. All integration tests use loopback mocks and disposable data.

### Implemented behavior

- M0071_KomgaIndex is NEW; published M0070 is unchanged. Adds auto_link=false,
  show_counter=true, link provenance and a persistent compact per-user index.
  Existing links default to MANUAL; AUTOMATIC records have current destination
  identity; OPTED_OUT records use empty series IDs as permanent unlink decisions.
- Manual confirmation/correction always wins over automatic linking. Unlinking
  persists an opt-out; refresh, restart, toggles and restore cannot recreate it.
  Explicit manual linking clears it. Failed correction preserves original link
  provenance/ID rather than falsely claiming manual confirmation.
- Matching is normalized exact primary/alternate title only: collapse whitespace
  including Unicode spaces, lowercase Locale.ROOT, preserve punctuation/numbers/
  edition text. Any competing primary/alternate-title candidate is ambiguous.
  Alternate-only matches require creator or authoritative URL corroboration.
  Known conflicting creators or same-host series URLs require review. A shared
  artist cannot hide another contradictory known local author: where remote
  contributor data exists it must contain all known local contributor names.
  This intentionally leaves incomplete/mismatched attribution for manual review.
  URL comparison ignores host case/fragments and preserves different paths/queries.
- Shared KomgaIndex jobs are demand-driven, single-flight, max four user jobs,
  one index fetch at a time. 15-minute freshness; 200 series/page; yield every
  five pages; max 250 pages/50,000 series, 32 MiB and 120 seconds plus an in-flight
  HTTP call timeout. Existing 4 MiB/8-second HTTP bounds remain. Thirty-second
  cooldown/error backoff, Retry-After up to an hour. No recurring server polling.
- Stage and atomically publish complete validated generations only. Reject
  inconsistent/truncated pagination, duplicate/malformed IDs and bad metadata.
  Failed/stale/incomplete indexes cannot create links. Preserve existing links on
  outage; hide counts for changed credentials/destinations and inaccessible or
  missing series; show eligible network-failure cache as stale. Auto links with
  contradictory title evidence get REVIEW summaries, never silent replacement.
- Series list sorting is name,asc, verified in official SeriesDtoDao source;
  url/id are not supported series sorts. Mock contracts reject unsupported index
  sorts. Detailed BOOK pagination retains its separate supported url,asc sort.
  Prefer read-only POST /api/v1/series/list, fallback once/client to older GET.
- Explicit Scan for matching Komga series works with auto-linking OFF. Reports
  indexing/matching progress and processed/total, automatically linked, already
  linked, ambiguous/review, opted-out and unmatched counts. Joining an incremental
  job still requests a complete library scan. Auto mode reacts to enabling,
  local library/metadata changes and successful index refreshes, without sources.
- Fresh-index metadata events match only changed manga: bounded 2,000-ID pending
  queue; overflow/new generations/manual scans use full library scans. Read
  existing decisions in bulk per 200-row batch. Events inside the cooldown wait
  for later demand/manual scan, not a scheduled job.
- Dedicated GraphQL settings and scanKomgaLibrary, komgaScanStatus and
  komgaLibrarySummaries (max 200 IDs). Summary reads are bulk local DB/cache;
  cards never call per-manga inventory. Generated UI types came from actual
  server schema export, not hand-written schema assumptions.
- WebUI settings toggles, scan action with active progress/outcome counts,
  manual/automatic/review display, automatic-link confirmation and opt-out help.
  Grid-level context deduplicates and batches summaries, publishes batch results
  progressively, partitions visible counts by destination and hides previous
  destination data even if replacement loading fails. Active refresh observation
  is bounded to 65 two-second waits; no recurring idle polling. Source/migration
  grids skip summaries. Hiding counts does not disable automatic linking.
- Both grid/list use the shared blue book badge: exact accessible count/tooltip,
  compact large visible numbers, stale asterisk and review question mark. Badge
  groups wrap only when Komga data is present, preserving existing counter values,
  colors and disabled/unlinked layout while avoiding extra horizontal crowding.
  Books remain distinct from verified chapter totals and Suwayomi download flags.
  Badge uses normal card navigation to manga details (no separate nested link);
  the Komga section has id=komga and existing Open in Komga/correction controls.
- Optional protobuf fields preserve preferences/provenance/opt-outs. Older backups
  retain safe defaults; imported connection disabled; imported links require
  reconfirmation. Existing MANUAL/OPTED_OUT choices win; backed-up deliberate
  choices can replace only unconfirmed AUTOMATIC links, using insert-if-absent
  followed by a conditional SQL update under the same transaction. Automatic
  inserts remain insert-only. This additional restore-priority fix and 20 new
  automatic-link/manual-confirmation/opt-out races passed final H2/PostgreSQL validation.
  Cache is disposable and excluded from portable backups. Sync exclusions
  and mounted API-key secret behavior are preserved. Single-user/no-login intact.
- No source requests, external metadata/file writes, download/progress changes or
  adaptive/Completed/tracker scheduling dependencies were introduced.

### Commit/push authorization — 19 October 2026

The user subsequently requested committing and pushing all current work to dev
for review. This supersedes the earlier commit/push prohibition only. The push
commit uses [skip actions] because adaptive-container.yml otherwise automatically
publishes an image on dev pushes. No image publication or NAS deployment is part
of this request. The implementation files below are included in that commit;
the earlier references to uncommitted work describe the pre-commit checkpoint.

### Final validation — complete, 19 October 2026

All requested local validation completed successfully. No test runner remains active.
- Full server suite: 470 tests passed, zero failures/errors/skips.
- ktlint passed; H2 migration/old M0070 manual-link upgrade passed.
- Isolated PostgreSQL 17.11: 25 prepare tests plus one real restart test passed.
  Fresh-install and upgrade table snapshots matched before/after restart; the
  disposable cluster stopped and temporary credentials were removed.
- WebUI: 60 tests passed, TypeScript, lint, full formatting and production/PWA
  build passed. The final UI-only fix keeps progress polling alive when Apollo
  reuses an unchanged status object; its regression test passed.
- Existing legacy-minifier and >500 KiB chunk warnings remain non-blocking.

Authoritative logs: server/build/komga-finalized-{format,server,ktlint,postgres}.log
and server/build/komga-ui-{types,lint,tests,format,build}.log.
PostgreSQL reports: C:/Users/bayleeh/AppData/Local/Temp/suwayomi-pg-cb389607bfab4897994814615dc49218
(prepare-results and restart-results). Backend source hashes in
server/build/komga-finalized-source.json still match. Two UI files changed after
that backend snapshot and were separately revalidated through all UI gates.

Coverage includes exact/Unicode titles, ambiguity, creator/URL conflicts, manual
precedence and permanent suppression, backup round trips and concurrent restore
races, stale/credential/deleted-series handling, malformed pagination/IDs, shared
fetches, cached summaries, large libraries and batches, H2 upgrade, PostgreSQL
migration/restart, GraphQL defaults/permissions, grid/list badges and touch layouts.
Provider tests cover 401 IDs in 200/200/1 batches. A pure index test covers 50,000
series. Existing application regressions passed in the full server suite.

Earlier failed/intermediate runs were resolved and superseded by these final
results: H2 test database selection, baseline-count expectation, JUnit Unit return,
unsupported series sort and overlapping Gradle report output. No unresolved test
failure is known. Run Gradle serially with the toolchain/flags below; overlapping
runners corrupt shared reports. The default Kotlin compiler heap was insufficient
for existing generated code, so the successful run used 3 GiB/in-process.

### Next-session instructions / review limitations

1. Read this section and docs/komga-integration.md; inspect dev/HEAD/status/diff.
   Preserve all 32 uncommitted files, including new files. No reset/clean/discard.
   No applicable AGENTS.md was found or modified.
2. Implementation and requested local checks are complete. Review the changes;
   do not restart development or repeat lengthy tests unless a new change or
   specific concern warrants it. No partially implemented code remains known.
3. Keep M0070 unchanged, manual/opt-out precedence, conservative validated-index
   matching, bounded shared batching/cache and source-free operation intact.
   Preserve single-user/no-login and backup safety. No scheduled polling.
4. Remaining validation is a future real NAS/browser/device and packaged-image
   smoke test; none is claimed here. Title metadata can produce conservative
   false negatives. Pagination is not transactional; collation ties or changing
   data can reject refreshes. Over-cap/stale/failed indexes cannot auto-link.
   The blue badge uses normal card navigation, without a separate jump action.
5. Nothing was committed, pushed, merged, published or deployed. The extension
   repository, Comix and NAS were untouched. Historical publication permission
   below does not authorize a new release. Obtain explicit publication authority
   before any future image release/deployment.

Toolchain: Java 21 at
C:/Users/bayleeh/.codex/tmp/suwayomi-r3549/jdk/jdk-21.0.12.1+1;
Node 24.22.0 and npm/node_modules/.bin/pnpm.cmd under the same suwayomi-r3549
root. Portable Python:
C:/Users/bayleeh/.codex/tmp/suwayomi-publication/python/python.exe.
Use explicit UTF-8 when editing. PostgreSQL binaries:
C:/Users/bayleeh/.codex/tmp/suwayomi-postgres/pgsql/bin.
Gradle flags: -I docker/adaptive/build.init.gradle --no-daemon --max-workers=1
-Dorg.gradle.jvmargs=-Xmx3072m -Pkotlin.compiler.execution.strategy=in-process.
The current full test uses per-task --rerun. A prior forced main/test cold
compile passed; do not repeat a separate cold compilation unnecessarily.
No system PostgreSQL service is installed/modified; the harness owns isolated
loopback clusters and removes plaintext credentials/stops them in finally.

Exact modified/new files (all intentionally uncommitted):
- CODEX_HANDOVER.md
- docs/komga-integration.md
- server/src/main/kotlin/suwayomi/tachidesk/graphql/mutations/KomgaMutation.kt
- server/src/main/kotlin/suwayomi/tachidesk/graphql/queries/KomgaQuery.kt
- server/src/main/kotlin/suwayomi/tachidesk/graphql/types/KomgaType.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/Library.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/Manga.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/models/BackupKomga.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/komga/Komga.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/komga/KomgaClient.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/komga/KomgaIndex.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/komga/KomgaMatching.kt
- server/src/main/kotlin/suwayomi/tachidesk/manga/model/table/KomgaTables.kt
- server/src/main/kotlin/suwayomi/tachidesk/server/database/migration/M0071_KomgaIndex.kt
- server/src/test/kotlin/suwayomi/tachidesk/graphql/KomgaGraphQLTest.kt
- server/src/test/kotlin/suwayomi/tachidesk/manga/impl/komga/KomgaMatchingTest.kt
- server/src/test/kotlin/suwayomi/tachidesk/manga/impl/komga/KomgaMigrationTest.kt
- server/src/test/kotlin/suwayomi/tachidesk/manga/impl/komga/KomgaTest.kt
- server/src/test/kotlin/suwayomi/tachidesk/test/PostgresValidationTest.kt
- webui/src/features/komga/KomgaBadges.test.tsx
- webui/src/features/komga/KomgaCard.tsx
- webui/src/features/komga/KomgaLibrary.test.tsx
- webui/src/features/komga/KomgaLibrary.tsx
- webui/src/features/komga/KomgaSettings.test.tsx
- webui/src/features/komga/KomgaSettings.tsx
- webui/src/features/manga/components/MangaBadges.tsx
- webui/src/features/manga/components/MangaGrid.tsx
- webui/src/features/manga/components/cards/MangaCard.tsx
- webui/src/lib/graphql/generated/apollo-helpers.ts
- webui/src/lib/graphql/generated/graphql-base.types.ts
- webui/src/lib/graphql/generated/graphql.ts
- webui/src/lib/graphql/komga/Komga.ts

## Latest handover: Komga auto-linking and library counters — 19 October 2026

### Current state and authorization

The latest user instruction prioritizes a complete handover BEFORE development or
lengthy tests. No commit, push, publication or deployment is authorized. Preserve
all uncommitted work. Do not reset, clean, discard or stash-and-forget. Stay on dev;
no new branch, master merge, history rewrite, NAS/production access, extension
repository access, Comix changes, downloaded-file changes or reading-progress edits.
Historical publication permissions below do not authorize any new publication.

Verified immediately before this update:
- Branch dev, tracking origin/dev, with no ahead/behind reported.
- HEAD 1e478517ad7288e1fe433c1899505db393633482.
- Subject: Record completed dev branch transition and workflow run.
- Commit time: 2026-10-19T12:46:11+01:00.
- Working tree was clean. Only CODEX_HANDOVER.md is intentionally modified by this
  task; no pre-existing uncommitted work existed or was discarded.
- Prior investigation checked live refs using git ls-remote: remote dev matched
  HEAD; old remote investigate/adaptive-library-updates intentionally remains at
  6a063c71ab5f0df13a515b02473bf87914cea0f4. Branch rename is complete.
- Prior investigation checked Actions run 39246941183 for source 74e2d90e using
  GitHub REST: in_progress, no conclusion. This is the last observation, NOT a
  current success or ready-image claim. No workflow was started in this session.
  URL: https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39246941183

AGENTS.md: checked repository recursively, including hidden files (excluding .git
and webui/node_modules), plus C:/AGENTS.md, C:/Users/AGENTS.md,
C:/Users/bayleeh/AGENTS.md and C:/Users/bayleeh/Documents/AGENTS.md. None found.
No AGENTS.md created or modified. Recheck if checkout/instructions change.

### Implementation status and inspected files

INVESTIGATION ONLY. Neither automatic linking nor blue counters is implemented.
There are no partial source changes, new tests, migrations, GraphQL changes,
generated artifacts or dependency updates. Exact modified file: CODEX_HANDOVER.md.
The prior assistant recommended a design and awaited approval. This latest request
approves the handover, not explicitly the implementation. Obtain explicit approval
before implementing. Detailed design choices below remain proposals, not an
already agreed implementation contract.

Read CODEX_HANDOVER.md and docs/komga-integration.md. Inspected:
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/komga/Komga.kt:
  manual link/upsert, unlink/delete, config identity, cache, backup/restore.
- Same directory: KomgaClient.kt, KomgaPagination.kt and KomgaInventory.kt:
  independent HTTP/secret handling, pagination and chapter comparison.
- server/src/main/kotlin/suwayomi/tachidesk/manga/model/table/KomgaTables.kt.
- server/src/main/kotlin/suwayomi/tachidesk/server/database/migration/M0070_Komga.kt.
- server/src/main/kotlin/suwayomi/tachidesk/graphql/types/KomgaType.kt,
  graphql/queries/KomgaQuery.kt and graphql/mutations/KomgaMutation.kt.
- server/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/models/BackupKomga.kt.
- server/src/main/kotlin/suwayomi/tachidesk/manga/model/table/MangaTable.kt.
- webui/src/features/komga/KomgaSettings.tsx and KomgaCard.tsx.
- webui/src/lib/graphql/komga/Komga.ts.
- webui/src/features/manga/components/MangaGrid.tsx and MangaBadges.tsx.
- webui/src/features/manga/components/cards/MangaCard.tsx, MangaGridCard.tsx
  and MangaListCard.tsx.

Existing links are manual; unlink deletes the row, so no persistent opt-out exists.
Connection identities already require reconfirmation after an API destination
change. Inventory cache is memory-only, max 16 snapshots, 15-minute access expiry,
60-second fetch freshness, 2-second manual-refresh minimum and 10-second error
backoff. A shared mutex serializes integration operations. Detailed inventory
fetches series details plus up to ten pages of 200 books with time/size bounds.
This path MUST NOT be used per library card. GraphQL has settings/search/details/
link/unlink/test/refresh, but no bulk summaries. Grid/list share MangaBadges.
MangaTable stores title, author, artist and realUrl; no dedicated structured
alternate-title field exists.

### Matching policy and persistent manual decisions

User requirements: Automatically link matching Komga series setting defaults OFF;
exactly one confident unambiguous exact-title candidate; normalize case/whitespace;
consider alternate titles/conflicting metadata; never overwrite manual links;
manual unlink must prevent automatic relinking without approval; allow correction;
persist links/overrides/opt-outs across restarts/backups; no source requests.

Recommended details pending approval:
- Trim/collapse whitespace including Unicode whitespace; case-insensitive matching.
  Preserve punctuation, numbers, subtitles and edition labels. No fuzzy matches.
- Count collisions across both primary and alternate titles. A competing alternate
  title makes a primary-title match ambiguous too; do not choose a candidate.
- Unique primary-title matches qualify only without contradictory known identity
  metadata. Reject clearly conflicting authoritative URLs or creator identities.
- Alternate-title-only matches require corroborating identity evidence, otherwise
  manual confirmation. Do not infer alternate titles from description text.
- Use only local Suwayomi metadata; never fetch sources to enrich matching.
- Persist MANUAL/AUTOMATIC provenance and per-user/manga opt-out state. Migrate
  existing links as MANUAL. Unlink writes a durable opt-out; settings toggles,
  refreshes, restarts and restores must not silently clear it. Only explicit manual
  linking or Allow automatic linking again clears the opt-out.
- Manual corrections/confirmations must win atomically over automatic jobs and
  backup restore. Show automatic provenance with Confirm/Correct/Unlink controls.
- Match in bounded local batches after complete index publication, enabling the
  setting, or local library/title metadata changes. No link writes in card queries.
- Identical titles with absent metadata cannot prove identity perfectly. Conservative
  checks will leave some valid matches for manual confirmation.

### Shared index/caching architecture: proposed, not implemented

Use one compact accessible/non-deleted Komga series index per user/connection and
credential identity: series ID, primary/alternate titles, available identity
metadata and reported booksCount. Paginated list requests replace per-manga search.
Official docs consulted: https://komga.org/docs/openapi/series/ . Current read-only
POST /api/v1/series/list is preferred; preserve compatible older GET behavior.
Installed NAS Komga version was not queried. Validate contracts with official
schemas and local mocks, not production.

Proposed initial limits, subject to approval and contract tests:
- 15-minute freshness; demand-driven refresh on stale use, no periodic polling.
- One single-flight refresh job per connection/credential identity. Return cached
  summaries promptly while refreshing; do not block card rendering on remote work.
- 200 series/page, five pages/work slice, max 250 pages / 50,000 series per complete
  refresh, plus explicit total time and response/memory bounds.
- Failure backoff, Retry-After support and manual-refresh cooldown. Bound fallback
  probes/retries as well; avoid repeated unsupported-route probes on every page.
- Example: 10,000 series requires about 50 successful list-page requests per expired
  refresh, spread across work slices; fresh library openings make zero Komga calls.
- Stage and atomically publish a complete validated generation. Partial/truncated,
  failed, cap-exhausted or inconsistent scans cannot establish matching uniqueness.
  No auto-linking from stale/incomplete indexes. Check duplicates/page totals.
  Komga pagination is nontransactional: concurrent rescans remain a limitation.
- Keep old data stale during refresh/outage; retain links. Never infer deletion
  from failed or partial scans. Deleted/inaccessible series retain decisions for
  correction but hide counts; rename never silently selects a replacement ID.
  Contradictory identity on an automatic link requires review.
- Persist compact summaries/index generations for restart usefulness. Partition by
  user/destination/credential fingerprint; secret rotation invalidates eligibility
  and cached visibility. Do not expose old credential access through stale data.
- Destination changes retain decisions but require reconfirmation as today. Keep
  secret-file auth server-side, including KOMGA_API_KEY_FILE behavior and rotation.

### Library-wide blue counter and batching

User requirements: separate optional blue Komga counter alongside unchanged
existing counters; stored BOOK count by default, not verified chapter count;
icon/tooltip identifying Komga; desktop/mobile support; no unlinked misleading
zero; stale cached information marked; no per-card Komga API requests; responsive
loading; hide-counter setting independent of integration enablement. Clicking
should ideally open linked Komga information.

Proposed implementation:
- Show Komga book counter setting, recommended on when integration is enabled;
  this display default is a proposal, unlike auto-link OFF which is required.
- New authenticated komgaLibrarySummaries(mangaIds), deduplicate IDs, max 200 IDs
  per batch. Bulk local DB/cache reads returning nullable count, provenance,
  availability, observedAt/stale and navigation destination. Schedule shared remote
  refresh separately. No per-card calls to existing komgaManga inventory resolver.
- Grid-level provider batches loaded/visible IDs, deduplicates across mounts and
  caches results. Define bounded debounce/batches and refresh-completion signaling
  without excessive polling. Cover virtualized vertical AND horizontal layouts.
- Use reported series booksCount from index. Detailed book enumeration/chapter
  matching stays on details pages. Preserve duplicate-edition/number handling in
  existing inventory comparison; never add counts to Suwayomi download flags.
- Fixed blue badge, small book icon, accessible Komga: N stored books label.
  Hide unlinked/unknown counts; linked known zero may show zero. Mark stale and
  expose observation time. Keep layout stable while loading.
- Shared MangaBadges handles grid/list. Test smallest mobile widths and large
  counts without crowding existing counters/options/continue-reading controls.
- Clicking opens manga's Komga section. Existing cards wrap badge content in
  interactive navigation: ensure valid markup, keyboard/touch access, selection
  and long-press behavior; avoid nested interactive elements and unwanted bubbling.

### Migration, GraphQL and backups

No schema/GraphQL change has been introduced. Recommend a NEW migration; check the
next available number before assigning. Do not rewrite released M0070. Add settings,
link provenance, durable opt-outs and bounded persistent index/summary generations,
with user/connection isolation, indexed bulk lookup and H2/PostgreSQL compatibility.

Extend settings GraphQL, summary types/bulk query and explicit opt-out reset/index
refresh as needed, preserving permissions and single-user/default-user support.
Regenerate WebUI GraphQL types from the real updated schema.

Add optional backward-compatible protobuf fields for preferences/provenance and
opt-outs. Existing manual links AND opt-outs win during restore, including races;
define old-backup defaults and conflict precedence. Keep imported connection
disabled and imported links requiring reconfirmation. Keep sync restore exclusions.
Index/cache is disposable: rebuild it, do not include it in portable backups.
Never back up the key or change its existing secret-file configuration.

### Validation status and remaining tests

For these two features: no tests, formatting, lint, TypeScript, production build,
migration or PostgreSQL checks have run; no application tests passed or failed
because implementation has not started. No lengthy runs attempted for handover.
Read-only Git and instructions inspection completed. Verify this documentation
change with git diff --check and ensure no other modified paths.

Historical baseline ONLY, not fresh feature results: prior combined r3549/Komga
publication passed 452 server tests, ktlint/JAR, 50 WebUI tests, TypeScript, lint,
formatting, production/PWA build and packaged GraphQL/assets smoke. Isolated
PostgreSQL 17.11/Java 21 harness previously passed 14 prepare + 1 restart tests,
including 20 manual-confirmation/restore races. See historical sections for exact
source/artifacts. Pending dev workflow is not a success claim.
Tool limitations: gh is not on PATH; public REST supplied the earlier workflow
status. python resolves to a missing Microsoft Store alias: an attempted handover
writer did not execute or change files. PowerShell was used instead. These are
local tool failures, not application test failures.

After implementation approval, cover:
- Exact title, Unicode whitespace/case, duplicates and alternate-title collisions,
  conflicts/missing metadata, alternate-only conservatism, rename handling.
- Defaults/toggles, manual correction/unlink/opt-out reset, restart and backup
  persistence, older backups and concurrent manual/automatic/restore decisions.
- Request counts, single-flight, cache freshness/backoff/cooldown, fallbacks/retries,
  large libraries, pagination inconsistencies, incomplete generations, byte/time
  caps, outage/stale, deleted/403/401 series, secret/destination changes.
- No source requests, remote writes, download/progress changes or credential leaks;
  authorization and existing no-login/default-user behavior.
- Desktop/mobile/list/grid badge, unknown versus zero, stale/hide setting,
  accessible click, selection/long press and no per-card remote requests.
- Existing Komga service/client/pagination/inventory/GraphQL tests, adaptive and
  historical learning, Completed automation, trackers and custom packaging.

Then run relevant server tests and full suite/ktlint, WebUI pnpm test:ui (pnpm test
is a placeholder), pnpm format:check, pnpm lint, pnpm tsc and production build.
Use isolated H2/PostgreSQL migration/restart/backup fixtures and current toolchain
instructions. No production data/remote services. Record actual commands/results.

### Actionable next-session instructions

1. Read this latest section, docs/komga-integration.md and applicable AGENTS.md;
   old publication permissions/history below do not supersede current restrictions.
2. Inspect status/branch/log/full diff. Expect dev at 1e478517 and only this handover
   modified unless the user adds work. Preserve every change; no branch creation.
3. Implementation has NOT started. Obtain explicit approval of the recommended
   design before code changes; handover-only instruction is not implementation
   approval. Resolve metadata-conflict rules, display default, index budgets,
   scheduling/notifications, credential invalidation and backup precedence.
4. Once approved: new migration/durable decisions and pure matching tests, then
   shared index/request-budget tests, then bulk GraphQL, regenerated types,
   WebUI provider/settings/badge and responsive/accessibility regression coverage.
5. Keep detail inventory isolated from summaries, manual precedence atomic and all
   matching local. Preserve r3549, adaptive/historical learning, Completed,
   tracking, current Komga integration, GraphQL, secret configuration and Docker.
6. Run relevant verification and update handover with exact modified files,
   migration/GraphQL changes, real results and outstanding tasks. Do not commit,
   push, publish or deploy without a new explicit instruction. Leave NAS,
   production library and extension repository untouched.


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

Completed: renamed locally, committed workflow/active-documentation updates as
74e2d90e57efda783b46fa7f5214a2a54dd7a2c2, and pushed dev with upstream origin/dev.
Both Git tracking and editor merge-base metadata now use origin/dev. The old
remote branch is retained at 6a063c71; deleting it was not requested. Neither
master nor the repository default branch was changed. No force push, NAS access
or deployment occurred. All existing application commits remain ancestors of dev.

Validation: workflow YAML parsed, push trigger equals dev, image tag configuration
is retained, git diff --check passed, and server/webui/runtime/smoke sources are
unchanged from the previous validated build. No application test rerun was needed
for this branch-name/documentation-only change. GitHub confirmed the workflow
triggered on dev for source 74e2d90e:
https://github.com/Chronosmage-alt/Suwayomi-Server-Personal/actions/runs/39246941183
This run is still in progress; its image is NOT yet claimed ready. The last
verified published digest remains recorded in the publication section below.
If this run succeeds, adaptive-test advances normally; repository/tag names stay
the same. Do not confuse a pending run with the previous successfully tested image.

The rename-status follow-up changes only this document and does not trigger an
additional image build. Next session: first inspect git status/branch/log and
this section, then check the existing dev workflow run before starting any new
build or development. Continue all future development on dev, preserving current
work and the no-NAS/no-extension/no-master-merge constraints.

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
