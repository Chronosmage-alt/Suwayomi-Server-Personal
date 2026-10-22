# Library loading and single-owner runtime — 22 October 2026

The reported delay is opening the library in any category. This setup has one
owner and currently uses no login. The changes preserve that access mode and the
existing owner (database user 1), library, chapter state, settings and backups.

## Library work reduced

The original category query requested six chapter-summary lookups per manga,
including data used by details/migration and every date-sort mode. The category
query now requests first-unread chapter data when continue-reading buttons are
enabled, and keeps the highest chapter number for migration. It requests last-read, latest-fetched or
latest-uploaded chapters only for the corresponding category sort. It omits the
unused latest-read lookup. Manga details still use the complete chapter fragment.
Chapter counts, read/download/bookmark badges, duplicate and tracker filters,
search metadata and Komga integration remain available.

The initial download-status request still fills the Apollo cache and receives
subscription updates, but runs in the background instead of holding the startup
splash until the full queue response arrives.

Library sorting uses immutable result references instead of deeply comparing
every manga and description. Refreshed card references use a map rather than a
linear search per card. Non-random sorts now update when chapter counts/dates
change without changing manga IDs; random order stays stable on data refresh.

## Measured fixture

A disposable local H2 database contains 300 manga and 150,000 chapters, split into
three categories of 100 manga/50,000 chapters. Each manga has read/download/bookmark
state and descriptive text. It uses no production data or source/Komga requests.

Ten alternating old/new requests used the same running baseline server and
category. The first two runs per query were excluded from the summary:

| Category query | Median | Range | Response characters |
| --- | ---: | ---: | ---: |
| Original | 732 ms | 408–1136 ms | 215,137 |
| Reduced | 258 ms | 180–762 ms | 187,425 |

The median decreased approximately 65%; response size decreased approximately 13%.
Both queries returned identical title IDs, chapter counts/flags, duplicate state,
continue-reading targets and highest chapter numbers. These are local synthetic
measurements under shared machine load, not a prediction for NAS timing, network
latency, cover loading or the user's particular category sort.

The comparison retained first-unread data in both requests to verify identical
continue-reading targets. Disabling those buttons now also skips that lookup;
the 65% figure does not include that additional reduction.

Fixture scripts, raw measurements and browser artifacts are outside Git under
`C:/Users/bayleeh/.codex/tmp/suwayomi-performance-20261022`. The baseline JAR and UI
are separate runtime copies and are never overwritten during a build.

## Single-owner runtime

The public GraphQL schema no longer exposes user listing, user lookup,
registration/invitation, role/permission administration or secondary-account
password/recovery operations. Optional login/refresh is restricted to owner 1;
secondary-account access and refresh tokens cannot authenticate to this runtime.
Current no-login access remains owner 1. Basic/simple owner authentication remains
available if enabled later. Owner permissions avoid repeated role-table queries.
Sync scheduling subscribes to the owner's settings without periodic discovery of
new users.

Existing user-related tables, foreign keys and backup data are retained for
compatibility; this is not a destructive database conversion. Historical account
implementation and tests use an internal compatibility schema, which the running
server does not expose. The real personal schema and owner-session behavior have
dedicated tests, and container smoke checks assert the account APIs are absent.

## Validation

- Full server regression suite: 480 passed, zero failures/errors/skips. Normal
  GraphQL tests exercise the personal production schema; four historical
  account/permission test classes explicitly opt into the compatibility schema.
- Frontend: 74 tests passed, including query selection, delayed download startup,
  live chapter-sort changes and stable random ordering. TypeScript, lint,
  formatting, schema codegen and English catalogue extraction passed.
- Production Vite/PWA build passed with the existing legacy-minifier fallback
  and chunk-size warnings.
- Headless Chrome checked all three categories at 1440x1000 and 390x844, with all
  three date-sort modes, and continue reading both off and on. Actual request
  variables matched each setting, enabled reading buttons appeared, and no page
  errors, GraphQL errors or horizontal overflow occurred. Screenshots were inspected.
- The packaged runtime smoke passed, covering custom WebUI assets/revision,
  database settings, Komga operations and absent account-management APIs.

Browser checks establish behavior and layout, not a reliable end-to-end speedup
measurement: fixture covers are placeholders, the host is shared, and the unrelated
official server-update prompt was dismissed. The alternating API comparison above
is the quantitative result. No NAS deployment or production-library timing occurred.
