# Completed-category automation

Local implementation pending review. No Docker publication or deployment.

## Existing single-user operation

`AuthMode.NONE` already maps requests to `UserType.Admin(1)` in
`server/user/UserType.kt`. Existing GraphQL authentication wiring injects that
identity into category operations. This feature uses exactly that path: it adds
no login, accounts, account selector or authentication changes. Existing category
and library ownership is retained for compatibility with the underlying code.

## Controls and behaviour

Library settings contains an optional switch (default off), a destination selector
showing category names, and Organise existing completed manga. The special Default
category is excluded because it represents the absence of explicit categories.

Successful metadata persistence reporting COMPLETED adds library manga to the
selected category once. Other categories remain. ONGOING never removes membership.
Chapter-only refreshes and failed metadata fetches do not trigger the rule.
Metadata success can trigger it even if a subsequent chapter fetch fails. Direct
manual metadata refresh remains available. Exclude Completed is independent.

A record keyed by user, manga and destination persists each handled combination.
Existing destination membership is also recorded when handled. Explicit removals
through existing category APIs record suppression, including removal before the
first automatic assignment while a destination is configured. Disabling and
re-enabling the rule does not erase these records. Changing destination allows one
addition there; switching back retains the old destination's handled record.
Deleting the manga/category/user removes its corresponding records by foreign-key
cascade. Recreating a category is a new destination, even if its name is reused.

Organisation previews added/already-present/previously-handled counts using only
stored COMPLETED statuses for library manga. Preview writes no assignments or
handled records. Apply recomputes counts and validates the destination again.
It works with automatic categorisation disabled, preserves other assignments and
respects manual removals. There is no source request or scheduled background job.
Operations are database-local. Existing rule-row locks serialize assignment,
organisation and manual removal; unique keys and atomic transactions prevent
partial bookkeeping or duplicate category membership.

Deleted destinations leave the rule effectively inactive. The UI warns and asks
for another category; no replacement is selected or created. Category names can
be renamed without breaking the selected ID. Settings reload when the settings
screen loads, categories change on that page, or the window regains focus, without
a polling timer.

## Persistence and portable backups

Migration M0069 adds a per-user rule table and a handled-record table. Keeping the
rule in a database row allows it to participate in the same locking/transaction
mechanism as category changes; it does not introduce user management. Full database
backups and ordinary restarts retain both tables.

Portable backups include rule preferences with Include user settings. Handled
records for included library manga require Include user settings and Include
categories. References use backup category orders and the existing category
restore mapping, never raw local category IDs. Handled records are merged without
deleting existing suppression, then the restored rule is activated after manga
restoration. Missing destination mappings leave an inactive rule and warning.
Internal category clears during restoration are not treated as manual removals.
Older backups without the new fields leave existing preferences and suppression
alone. Sync restores do not import these server-local automation controls.

To transfer the complete rule, include user settings, categories and manga in the
backup and restore. A library-only portable backup cannot transfer handled records
for manga omitted from that backup; a full database backup retains them all.

## Boundaries

The feature does not mark chapters read, alter progress, create/delete downloaded
files or edit download queues. Existing category update/download inclusion rules
still apply after category membership changes. Source Completed may precede final
translated uploads, and stored statuses used by Organise may be stale. No new
network check is performed to resolve that uncertainty. Titles already Completed
when added to the library are processed at their next metadata refresh or by
Organise; there is no additional refresh triggered on library insertion.

## Validation, 17 October 2026

Full server suite: 410 tests passed, zero failures/errors/skips; server ktlint
passed. WebUI: 13 tests passed, TypeScript, lint, changed-file formatting and
production Vite build passed. Regressions cover the existing no-login/default-user
GraphQL path, ownership compatibility, atomic concurrent application/removal,
metadata-only success/failure, unchanged chapter progress/download flags, local
preview/apply, deleted destinations, gzip portable backup export, category-ID
remapping and preservation of manual opt-outs during restore. Internal restore
clears do not manufacture manual suppression for legacy backups. New optional
protobuf fields have safe defaults and existing handled records are merged.

These are isolated H2/synthetic-source tests. No new live-source, PostgreSQL, NAS
or server-process restart test was performed. Persistence relies on database rows
rather than process-local state. No publication or deployment was performed.

Final combined review: GraphQL removal and clear operations before first automatic
handling, plus deleted-destination reporting, have additional regression coverage.
Full server validation now passes 412 tests; WebUI remains 13 tests, with ktlint,
TypeScript, lint, changed-file formatting and production build passing. Migration
interactions are documented in `source-migration-review.md`; its behaviour was not
changed by the review.
