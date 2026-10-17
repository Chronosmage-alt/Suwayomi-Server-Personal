# Source migration review ? 17 October 2026

Investigation only. No migration behaviour was changed.

## Record identity

The WebUI implements migration in `webui/src/features/migration/MangaMigration.ts`
as ordinary fetch/update/category/chapter/tracker/metadata calls, rather than a
single server-side move. The old manga's source reference is not changed.
`MangaList.MangasPage.insertOrUpdate` looks up destination entries by source ID and
URL, reusing an existing record or inserting one during browse/search processing.
Thus the destination ID can already exist before the user confirms migration,
even if it has never been added to the library. Not-in-library does not mean new.
Self-migration to the same manga ID is rejected.

## Adaptive controls and scheduling

The migration queries and copy operations do not read or write adaptive settings.
Those are in `adaptive_manga_state`, not manga client metadata. The optional
metadata-copy setting therefore does not transfer SMART/NORMAL/FOLLOW_SOURCE or
release learning.

A new destination starts FOLLOW_SOURCE. An old SMART or NORMAL override is not
carried over; FOLLOW_SOURCE naturally follows the destination source's own policy.
An existing destination keeps its mode. Source policy switches are independent and
are not copied either.

Observations, learned intervals, next-check/backoff state, provisional predictions
and historical rejection markers are not copied between IDs. The old record keeps
its state. A destination fetch uses the normal learner, so a new destination can
seed a provisional hint from its own raw source dates and must confirm it normally.
Existing destination state is not overwritten by migration, but a normal refresh
can legitimately advance or invalidate it based on the destination's response.

Recommended future change (not implemented): carry the chosen title mode only to a
truly new destination, never carry source-specific timing, and preserve existing
destination modes and learning. Creation provenance must be identified explicitly;
checking only library membership would overwrite preferences on existing records.
Source-relative FOLLOW_SOURCE should continue to use the destination policy.

## Categories and Completed automation

With category migration enabled, old categories are added to the destination;
existing destination categories are retained. This includes a Completed category
whether or not the new source currently reports COMPLETED. Automation never removes
it on ONGOING. Category migration is enabled by default but remains configurable.

Completed handled records are keyed by user, manga ID and category ID. They stay
with their original record and are not copied by migration. Existing destination
handled records are preserved, protecting later automation/Organise. A new
record does not inherit an old record's manual opt-out and can subsequently be
categorised after a qualifying metadata refresh or Organise action.

Explicit category-copy operations can add a category even when automation would
skip that destination; add-once suppression controls automation, not deliberate
category edits. Whether migration should carry old manual opt-outs or skip copied
categories is a separate migration policy decision, not implemented here.

In move mode the old title is removed from the library. Existing settings can
also clear its categories; that option defaults off. If enabled, this currently
uses the same GraphQL clear operation as manual editing and records an opt-out for
the old title's configured Completed destination. This is a current interaction,
not a migration change made in this review. Copy mode leaves the old library title.
The old database record is not deleted by these operations, so its learning,
handled records and chapter-user state remain.

A destination metadata fetch can already run during migration preparation. If it
is already in the library and reports COMPLETED, normal categorisation can occur.
If it is not yet in the library, that fetch does not categorise it; library addition
itself does not trigger this rule. Category copying, a later metadata refresh or
Organise determines subsequent membership.

## Reading state, files and partial operations

When chapter migration is enabled, the WebUI marks destination chapters at or
below the old highest-read chapter number as read. Matching numbered bookmarks are
added. This is a range-based read transfer, not an exact unread-gap/page-progress
copy: `lastPageRead` and read timestamps are not copied by this migration helper.
Existing destination read/bookmark state is not cleared. Optional chapter metadata
and manga metadata copying can overwrite destination app-metadata keys; that is
separate from the adaptive tables and Completed handled table.

Downloaded files are not copied. The existing Delete chapters option can delete
old downloaded chapters in move mode (the option defaults on). This is pre-existing
migration behaviour, not a side effect added by the two features under review.
Tracking bindings may also be migrated according to the selected option.

Copy operations precede cleanup, but each stage uses multiple requests and is not
one transaction. A failed copy prevents cleanup from starting, while some other
copy requests may already have succeeded. No additional migration atomicity or
rollback implementation was added.
