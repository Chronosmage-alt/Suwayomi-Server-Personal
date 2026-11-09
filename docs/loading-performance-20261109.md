# Library and Updates loading — 9 November 2026

Investigated the report of Library/Updates taking up to a minute to show results,
with approximately 300 library manga and a likely H2 database.

## Findings and fixes

- The Updates query requested the total number of matching library chapters, even
  though the screen only uses nodes and pagination. That forced a count across
  the full library on each request. The query no longer requests that count.
- Updates cards also requested manga read/download/bookmark totals, duplicate
  detection and chapter counts that the cards never render. Those aggregate
  fields are removed from the Updates fragment. Library/details/history retain
  their required statistics.
- Initial-import notification suppression previously used the shared query's
  total count. It now checks whether the existing `first: 1` request returned a
  node, preserving the same existence test without a whole-result count.
- Updates invalidation copied Apollo's entire entity cache once per distinct
  manga. Its effect depended on the complete hook result, so unrelated renders
  could repeat this work. It now depends on the actual chapter-node reference
  and invalidates affected root chapter-list fields in one cache modification.
- The permanent updater subscription used the same per-manga full-cache copying
  for completed entries (including retained history). It uses the same one-pass
  invalidation now. This background work affects Library as well as Updates.
- Manga IDs are matched exactly instead of by string prefix: an update for manga
  1 no longer invalidates manga 11's chapter list. Global Updates/History lists,
  unaffected manga lists and normalized chapter entities are preserved.

Existing pagination/scroll restoration, library filters and sorting, badges,
continue reading, initial-import suppression and excluded scanlators are retained.
No server code, database schema or production data changes are involved.

## Measurements

The fixture is a separate copy of the previous synthetic 300-manga/150,000-chapter
H2 database, migrated through M0073 using the current server JAR. No production
library, source fetch or Komga endpoint is used.

Six alternating before/after requests against the same running server fetched
150 Updates entries with the screen's actual GraphQL document. The comparison
asserted identical displayed chapter/manga fields and pagination.

| Measurement | Before | After |
| --- | ---: | ---: |
| Median Updates request (six runs each) | 1,594 ms | 43 ms |
| Response size | 81,375 bytes | 64,105 bytes |
| Median cache invalidation (three runs each) | 7,182 ms | 9 ms |

The cache fixture contains 20,000 normalized chapter entities and 300 manga-list
fields, with one event covering 300 completed manga. The previous implementation
extracts the whole cache 300 times; the new implementation extracts it zero times.
These are synthetic local measurements under shared machine load, not predicted
NAS timings or a reproduction of the user's exact minute-long delay.

Library's complete 300-manga query still takes roughly 1–3 seconds in this local
fixture, depending on concurrent load. Its statistics and unread-chapter lookups
are real work. This change removes the shared browser-side blocking; it does not
claim a measured reduction in the Library SQL query. A read-only trace of the real
server/browser is needed to attribute any remaining delay on the NAS.

## Validation

- All 99 WebUI tests passed. Cache regression tests cover exact ID matching,
  one-pass invalidation without entity-cache extraction, empty events and
  preservation of unrelated lists/entities. Notification tests cover existing
  chapters and first imports using the bounded existence result.
- TypeScript, lint, formatting and GraphQL generation passed. Generated operation
  types reflect the smaller Updates selection; server/base schema types remain
  unchanged.
- Production Vite/PWA build passed with the existing legacy-minifier fallback
  and chunk-size warnings. Git whitespace checks passed.

Logs, raw measurements and the disposable fixture are outside Git under
`C:/Users/bayleeh/.codex/tmp/suwayomi-loading-20261109`.
The task-owned fixture server was stopped after measurement. No push or deployment
is included.
