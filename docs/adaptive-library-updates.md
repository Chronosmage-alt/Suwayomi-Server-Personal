# Adaptive library updates

Development branches: `investigate/adaptive-library-updates` in the server and
`D:/Git/Suwayomi-WebUI-Personal`. The UI baseline is commit `41443eab`, the exact
source revision for the configured r3518 bundle, not current master.

## Chapter evidence

The four local extension parsers were inspected without modifying the extension
repository or requesting chapter lists from their sites. Representative test
lists are synthetic fixtures based on those parser shapes, not captured live data.
Comix can combine fresh chapters with its previous list and can expose multiple
scanlators for a number. Asura has chapter creation dates and locked chapters.
MyReadingManga parts share one post date. Yaoi Manga Online can omit chapter dates
and numbers. Consequently there is no safe universal chapter-count classifier.

V1 treats up to three newly advanced, recognized chapter numbers as one candidate
event. Larger forward additions are ambiguous bulk additions: normal checking
continues and learning restarts. This favors extra requests over a false schedule;
legitimate larger batches do not receive adaptive savings in this version.
Initial lists, URL replacements, duplicate numbered scanlators, and backfills do
not become release events. Raw incoming dates are inspected before Suwayomi adds
fallback dates. Discovery-only observations require a preceding check within a
day. Four consistent observations are required unless a reliable historical candidate is confirmed by two new releases; no seven-day default is used.

## Delivery and operations

The master switch defaults off. Enable it and individual sources in Library
settings, or set a title override. Source policies and title overrides are shared
server settings. Manual refresh bypasses adaptive timing, retaining extension
protections. The normal global update interval and exclusions still apply.
Guard spacing is half the learned interval, capped at seven days. Hiatus eligibility
gaps are capped at fourteen days. Downtime, exclusions, queueing and source failures
can delay actual execution beyond eligibility.

Both the modified server and modified UI are required for the complete feature.
Deploy only after review; keep a database/data-directory backup. Prevent automatic
WebUI replacement with an upstream bundle when using a locally built custom UI.
No extension rebuild or modification is required. Adaptive scheduling does not
guarantee protection from IP blocks, and it does not coordinate downloads with
updates. Extension cooldown deadlines are not exposed by the current source API.

## Implemented integration

`Updater.kt` identifies automatic jobs and evaluates eligibility from database
state before enqueueing source work. Manual library/category/title refreshes bypass
adaptive timing. `Manga.kt` checks automatic eligibility again inside the shared
per-title fetch lock and coalesces checks completed after a job was queued.
`Chapter.kt` records observations only after successful chapter reconciliation.
Skipped jobs retain chapters and metadata and expose their reason and eligibility
deadline through GraphQL. Existing exclusions and source update strategies apply.

Migration `M0068_AdaptiveUpdates.kt` creates independent source-policy and manga-state
tables. Source policies survive extension removal; the same source ID and provider
package recover their preference on reinstall. Title overrides and bounded release
history persist in the server database. The global switch is persisted in server
configuration. Upserts preserve independently changed controls and observations.
Portable backups with the applicable manga/server settings flags retain controls;
restoring chapters resets derived learning. Ordinary synchronization does not.

GraphQL queries expose settings and title schedules; mutations require the existing
manage-settings permission. The WebUI includes global/source switches, retained
uninstalled preferences, title overrides, reset-learning controls, and explanations
of learning, exclusions, eligibility and the next global update opportunity.
The r3518 UI also handles this personal server's existing user-settings backup flag.

## Schedule changes and tradeoffs

Checks before an expected release retain half-interval guard spacing, capped at
seven days. A substantially earlier qualifying release immediately returns a title
to normal checking while it relearns. Two consecutive conflicting observations
also reset learning. Successful manual refreshes contribute observations, so they
can reveal a faster cadence before the next automatic guard. Four fresh consistent
observations are needed before adaptive timing resumes.

An expected release missed by a guard receives normal checking for a 48-hour grace
period, then increasing empty-check intervals of 1, 2, 4, 7 and 14 days. Fourteen days
caps eligibility spacing, not a promise of execution by that date. A new release
during backoff restarts learning. Daily releases retain normal checking.

A reliably weekly title can approximately fall from fourteen checks per week with
a 12-hour global timer to two guard checks per week. Discovery of an early release
can consequently lag by approximately 3.5 days plus the global timer/queue delay.
Longer learned intervals permit up to seven days between guards; hiatus backoff
permits up to fourteen. Undated, irregular or large-batch titles may receive few
savings because ambiguous evidence deliberately keeps normal checking.

## Validation

The complete server suite passed: 386 tests, zero failures or errors. Added tests
cover release classification, four-observation learning, guard/backoff bounds,
faster/ambiguous cadence changes, persistence, source identity, backup compatibility,
automatic-job deduplication, initial/manual refreshes, failed-source checks and
GraphQL permissions. Server ktlint checks passed.

The modified UI passed TypeScript checking, lint, changed-file formatting checks
and a production Vite build. Its upstream test command contains no meaningful unit
suite. Isolated local browser checks verified global/source controls and title
overrides, manual refresh, explanations, desktop/mobile rendering and no horizontal
overflow or browser errors. A real server-process restart retained settings and a
synthetic weekly schedule in an isolated H2 database. No personal library was used.
PostgreSQL, live source chapter lists and NAS deployment were not tested.

### Readiness-review fixes, 17 October 2026

Automatic deduplication and post-refresh coalescing now require the adaptive master
switch. Disabled mode retains the original repeated source requests, while the
shared title lock still serializes fetches safely. Requests have distinct internal
IDs: active work is tracked independently of the latest title status, so an older
job cannot overwrite a pending manual request or make the updater falsely idle.
Category preparation also counts as active work. Starting a library/category
refresh no longer cancels existing requests; the explicit Stop action still does.

Notifications are serialized, read current reserved job states, and publish initial
statuses before dispatching source work. Synthetic category-level pending jobs have
been removed. Source-channel failure accounting is limited to that source, and
completion of an old channel cannot remove its replacement.

Reset-learning and restoration of an existing title share the title fetch lock.
They wait for a running fetch to finish before resetting history or restoring data,
without holding a database connection while waiting. Restore resets history inside
its transaction without acquiring the same lock twice. Consequently these operations
may wait for a slow source; they do not bypass or cancel extension protections.

The updater toolbar now opens a skipped-updates dialog. It distinguishes adaptive
deferment, already-checked/no-longer-due work and existing eligibility filters, shows
the eligibility deadline when available, and links to each title. The progress
control handles zero-job updates and waits for actual idle status before announcing
completion. Existing-filter explanations are grouped rather than identifying each
individual unread/category/completed filter.

Six additional regression tests cover disabled-mode repeated requests, overlapping
automatic/manual/category requests and a scheduled trigger, fast-source notification
ordering, reset during a paused fetch, chapter restoration during a paused fetch,
and completed-title exclusion with the master on/off and every title override.
The completed-title test also verifies direct manual refresh and that automatic
updates resume when the existing completed exclusion is disabled.

UI validation was repeated: TypeScript, lint, changed-component formatting and the
production build passed. A browser test used fixture updater responses alongside
the isolated real server to check all three skip reasons, eligibility dates, title
links, zero-job progress, desktop/mobile layout and dialog dismissal, with no browser
errors. These checks do not represent live source or Linux Docker validation.

Assessment: ready for an isolated Docker smoke test after approval, using a fresh
data directory and no production/Komga mounts. Production rollout remains pending
Linux WebView checks, actual NAS database/mount inventory, migration/restore and
rollback rehearsal on a stopped copy. The matching pinned r3518 UI remains selected;
no upstream merge was performed. Single-user/no-login operation uses the existing
default-user and permission architecture; no account special case was introduced.

## NAS configuration after separate deployment approval

1. Back up the persistent Suwayomi data directory/database before the first start
   of the modified server. Keep that persistent volume: migration and learning
   state live there, rather than in the container image.
2. Install both the modified server and the matching modified WebUI. Configure
   WebUI flavor **Custom** and `server.webUIUpdateCheckInterval = 0` to prevent
   an upstream bundle replacing the custom UI. Place the built UI in the configured
   WebUI directory (normally `<data directory>/webUI`), including its revision file
   for server version reporting. No extension changes are needed.
3. Keep automatic library updates enabled with the desired existing interval and
   exclusions. Enable adaptive updates in Library settings and select sources.
   The master switch defaults off; title overrides can follow source policy,
   request adaptive updates, or retain normal updates. Shared controls require
   manage-settings permission.
4. Allow normal checks to establish four qualifying observations, or confirm a
   provisional historical pattern with two new releases. Historical dates alone
   never reduce checks. Manual refresh remains available but
   still respects extension pacing/cooldowns and may fail during a cooldown.

Downloads retain their existing dispatch behavior. There is no server-visible
extension cooldown deadline, so the UI cannot promise when a source cooldown ends.
All implementation changes remain uncommitted; nothing has been deployed or published.

## Provisional historical learning (local implementation, pending review)

Normal successful chapter refreshes can analyse the raw source dates already
passed to the learner, before database fallback dates are substituted. No extra
source requests or database migration are needed. Persisted historical candidates
are separate from newly observed releases; existing version-1 state reads with
safe defaults and existing learned schedules are retained.

The classifier inspects at most the latest 24 distinct numbered chapters in number
order. URLs and chapter numbers are deduplicated. Missing, future or reversed dates
and duplicate-number date spreads greater than 12 hours reject the window. Chapters
within 24 hours of a group's first date count as one event, with at most three
distinct numbers; larger groups reject the window. At least six events are needed.
The last six must have five intervals consistent with their median, within the
existing tolerance of one day or 20%, whichever is greater. Candidate intervals
must exceed two days and be at most 60 days. The latest event must be no older than
one candidate interval plus tolerance. These conservative boundaries are covered
by synthetic weekly/monthly, jitter, stale, duplicate, missing-date and bulk tests;
they are not measurements of live source reliability.

Normal checks continue while two subsequent qualifying forward releases confirm
the historical anchor and then each other. Each confirmation refresh revalidates
the current raw historical dates; missing or inconsistent evidence rejects the
hint even when discovery timing could qualify for ordinary learning. Historical
entries never inflate the
fresh observation count. A contradiction, ambiguous change or missed prediction
(interval plus tolerance plus two days) discards the hint while retaining the
existing learner's result. Four-observation learning continues independently.
After rejection, another candidate needs six events beyond the rejection's chapter
number watermark, which never decreases when source lists shrink. Reset or chapter restoration suppresses the current history on
the next successful refresh, then permits only newer history. Stale-state expiry
preserves that suppression. These fields persist in the existing JSON state;
portable chapter restoration still resets derived learning rather than importing it.

The WebUI shows the provisional interval and zero/one of two confirmations, and
allows resetting a candidate before any fresh observations. Guard checks, manual
refresh, controls, exclusions and the 14-day backoff maximum retain existing rules.
Sources can still provide convincing but incorrect dates, so this feature cannot
prove original publication timing. Unreliable or unavailable evidence uses normal
four-observation learning.

Validation on 17 October 2026: full server suite 392 tests, zero failures/errors or
skips; server ktlint passed. WebUI: eight tests passed, TypeScript, lint,
changed-file formatting and production Vite build passed. The JCEF download test
required the documented Windows-ROOT trust configuration; the Kotlin compiler
required additional local heap. No new live-source, browser visual or NAS test was
performed for this change. All changes remain local and uncommitted.

### Focused historical-learning review

The review found and fixed three edge cases: raw historical dates becoming
unreliable during confirmation, suppression watermarks decreasing after shortened
source responses (or forgetting a confirmed chapter during stale-state expiry),
and chapter restoration/reset failing to persist suppression when no adaptive row
existed. Repeated refreshes/imports cannot confirm a candidate; the release that
first creates a hint is not counted as its first confirmation. Regression tests
also cover persistence reload between confirmations, independent four-release
fallback, manual refresh/failure/reset request counts, and actual chapter restore
without a pre-existing state row. No source request path was added.

Review validation: full server suite 401 tests, zero failures/errors/skips; ktlint
passed. WebUI eight tests, TypeScript and lint passed again; frontend source and
GraphQL contract were unchanged by the review. Restart continuity was checked via
persisted-state reload between releases; no new server-process or NAS restart test
was performed. Changes remain local and uncommitted.

Final review: reset/chapter restoration now retains the known historical rejection
watermark while clearing active schedules and observations. A truncated source
response after reset cannot lower that horizon and revive old history. Ordinary
four-observation learning continues independently. Source migration behaviour is
investigated separately in `docs/source-migration-review.md`; no migration changes
were implemented.


## Historical diagnostics and conservative date provenance (local, pending review)

Each enabled successful chapter refresh records one bounded assessment. It exposes
reason, assessment time, inspected-number/date-origin counts, grouped events, and
where available an interval and latest-event age. It does not store or log chapter
lists, titles, scanlators or plaintext URLs for diagnostics. The UI displays human
explanations independently of fresh-observation counts and provisional confirmations.
A null assessment means "not assessed since this update", including legacy databases;
source errors do not overwrite the last successful assessment.

The classifier still inspects a contiguous tail of at most 24 distinct chapter
numbers, without selecting only convenient dates. The first decisive rejection is
reported. Group counts can be partial when assessment stops at unreliable evidence.
Missing, unknown-origin and fallback dates cannot seed or revalidate a historical
hint. Existing duplicate/date grouping, six-event, two-confirmation, consistency,
recency and bulk/backfill safeguards are unchanged.

Server-owned adaptive JSON also retains at most 96 opaque SHA-256 fingerprints of
chapter URL plus persisted date and their origins. No new SQL tables or columns are
needed. The refresh integration supplies both extension output and actual dates
stored after reconciliation. Zero/missing output remains missing for that assessment;
a newly generated substitution is recorded as fallback for later refreshes. Retaining
an existing database date preserves its recorded origin, or unknown if no proof exists. Unchanged
legacy values remain unknown, even when an extension returns them with nonzero dates.
Same-date replacements under changed URLs also inherit conservative stored origins.
Evicted evidence becomes unknown when subsequently reused. Enabling adaptive mode
later does not retroactively certify dates collected while it was disabled.

"Extension-reported" means the server observed a nonzero value not matching unknown
or fallback stored evidence and newer than the retained evidence horizon, or preserved an already recorded extension-reported
origin. It does not prove fresh network parsing, exact publication timing, or absolute
source correctness. Comix can preserve stored dates, return unchanged input, and parse
coarse relative dates. Without extension cooperation those limitations cannot be
eliminated. Conservative handling may prevent historical acceleration for an existing
library; normal four-observation learning remains available and independent.

The summary and ledger survive ordinary restarts and full database backup/restore.
Manual reset and portable chapter restoration clear date proof and stale assessments,
retain existing suppression/watermark rules, and show a reset/restoration suppression
message with no fabricated assessment timestamp. A later refresh assesses restored
dates as unknown. Expiring stale adaptive state retains date origins, but drops the
old provisional hint and exposes expiry only when an assessment previously existed.
Existing active schedules are not retrospectively invalidated; historical hints are
revalidated at the next enabled chapter refresh. No new source requests, polling,
background jobs, extension changes, or changes to Comix request protections are added.


Diagnostics/provenance validation: 428 server tests passed with zero failures, errors
or skips; ktlint passed. All 21 WebUI tests, TypeScript, lint, changed-file formatting
and production build passed. Tests use synthetic source responses and isolated test
databases; they do not establish this manga's actual live Comix rejection reason.


### Focused provenance review

A monotonic `dateEvidenceThrough` watermark prevents forgotten evidence from gaining
trust: a returned date not matched to retained evidence/current stored chapters is
UNKNOWN when at or before this watermark. Successful-check time and the largest
observed timestamp advance it, including malformed future dates. The scalar survives
reset and state expiry even when detailed fingerprints are cleared; legacy lastCheck
provides a lower bound. Thus deleted, reuploaded or evicted unknown/fallback dates
cannot be newly certified merely because their old entry vanished or wall time
caught up. Known origins follow unchanged dates through reordering/replacements;
losing a specific fallback record can yield UNKNOWN, never new trust.

This is deliberately conservative: changed older dates or an erroneous future date
may prevent a historical shortcut. The independent four-observation learner remains
available. A genuinely new timestamp beyond the horizon is still only an extension
assertion; the server cannot prove fresh parsing or publication accuracy. All existing
historical reliability thresholds and source request protections remain in place.
