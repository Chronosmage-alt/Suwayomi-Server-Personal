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
day. Four consistent observations are required; no seven-day default is used.

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
4. Allow normal checks to establish four qualifying observations. Existing chapter
   dates alone do not seed a prediction. Manual refresh remains available but
   still respects extension pacing/cooldowns and may fail during a cooldown.

Downloads retain their existing dispatch behavior. There is no server-visible
extension cooldown deadline, so the UI cannot promise when a source cooldown ends.
All implementation changes remain uncommitted; nothing has been deployed or published.
