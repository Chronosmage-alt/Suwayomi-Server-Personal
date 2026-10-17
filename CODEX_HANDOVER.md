# Suwayomi adaptive updates — handover

Updated 17 October 2026. This document is context for a fresh Codex conversation.

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
