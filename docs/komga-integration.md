# Optional Komga library integration

The permanent development branch for this custom server and bundled WebUI is
`dev`, tracking `origin/dev`. It replaces `investigate/adaptive-library-updates`;
all r3549, adaptive scheduling, Completed-category and Komga work is retained.
The image repository and tags remain unchanged, including `adaptive-test` and
`sha-<12-character-commit>`. Pushes to `dev` matching the workflow's build paths
continue running the existing server/WebUI/container validation and publication.
The previous remote branch remains available for reference; new work belongs on
`dev`. The separate extension repository and historical WebUI checkout are unchanged.

The integration starts disabled. It reads inventory and the Komga API-key user's
reading progress alongside existing trackers. It does not update Komga, reading
progress, Suwayomi download flags, files, source scheduling or update queues. No
manga-source requests are made. Existing no-login installations continue to use
Suwayomi's default user; there is no new account-management screen.

## Automatic linking and library book counters (M0071)

The integration still starts disabled. **Automatically link matching Komga series**
starts off; **Show blue Komga book counter** starts on and is effective only when
Komga is enabled. Both options are in Settings -> Tracking -> Komga. Save before
using **Scan for matching Komga series**. The scan is available even with automatic
linking off: it is an explicit one-off request to process the existing library.

The scan returns immediately and reports indexing/matching progress followed by
processed/total, automatically linked, already linked, ambiguous/requiring review,
opted-out and unmatched counts. Errors and cooldowns are reported as status codes;
a failed index creates no automatic links. A scan can join an in-progress index
refresh. Progress requests only observe active work; no scheduled server polling
or recurring library scans are installed. Automatic mode reacts to enabling the
setting, local library additions/metadata updates, and demand-driven index refresh.
With a fresh index, local events match only changed manga, using a bounded queue
of up to 2,000 IDs per user; overflow requests a full local scan. Events during
the 30-second cooldown wait until later demand or a manual scan. New index
generations and explicit scans process the full library. Each work batch reads
existing decisions in one bulk query rather than querying each linked manga.

Matching uses only stored Suwayomi title/author/artist/URL and the shared Komga
index. It collapses whitespace (including Unicode spaces), ignores title case,
and preserves punctuation, numbers and edition/subtitle text. Multiple candidates
across primary AND alternate titles are ambiguous. For this personal library,
a unique primary exact-title match is sufficient even when author, artist or
stored URL credits differ. This policy was explicitly requested after the
Absolute Regression example (studio vs individual artist and bilingual credits).
An alternate-title-only exact match still needs creator or URL corroboration and
must have no known creator or authoritative same-host URL conflict. For those
alternate matches, available remote contributors must contain all known local
contributors, so a shared artist cannot conceal a conflicting author. URL identity
ignores host case/fragments while preserving path/query differences. Matching
is not fuzzy: spelling, punctuation, numbers and edition text remain significant.
No fuzzy matching or source refresh is performed.

Links show MANUAL or AUTOMATIC provenance. Existing links migrate as MANUAL.
Automatic links can be confirmed or corrected in the manga's Komga section.
Unlinking stores a permanent OPTED_OUT decision, rather than merely deleting a
row. Restart, refresh, settings toggles and backup restore cannot undo it. Explicit
manual linking clears the opt-out. Existing manual decisions win over automatic
insertions and backup imports. Restored manual links/opt-outs replace unconfirmed
automatic decisions only; current manual confirmations and opt-outs are never
overwritten. Insert-if-absent plus a conditional update is atomic on H2 and PostgreSQL.

### Shared index and request limits

One compact complete series index per user/connection/credential identity is
persisted in the database. A bounded four-user memory cache accelerates lookups.
No per-card series search or detailed book inventory is requested. With automatic
linking off, empty batches and batches containing only unlinked/opted-out manga
do not fetch an index they cannot use; already cached match information can still
be read locally. Index lists
prefer read-only POST /api/v1/series/list and fall back once per client to older
GET listing on 404/405, ordered by name ascending (a supported series sort;
book inventories retain their separate url sort). All HTTP credentials remain
server-side and retain the existing API-key secret-file configuration.

Freshness is 15 minutes. Stale use starts a shared demand-driven job, with at most
four outstanding user jobs and one index fetch at a time. Pages contain up to 200
series, yielding every five pages; a full fetch is capped at 250 pages / 50,000
series, 32 MiB of response data and a two-minute elapsed budget (an in-flight call
can finish beyond that budget, within the HTTP timeout). HTTP responses retain
the existing 4 MiB cap and eight-second call timeout. Refresh attempts have a
30-second cooldown/error backoff, extended by Retry-After up to one hour.

Pages must provide consistent totals, page size and valid metadata; duplicate IDs,
truncation, malformed pages or exhausted budgets prevent publication. A complete
validated generation replaces the previous one atomically. Automatic matching uses
only a fresh complete generation and rechecks destination/settings/credentials
between work batches. Komga does not provide a transactional snapshot: concurrent
rescans with unchanged totals and collation ties remain limitations; explicit refresh later can
resolve changed data. A 10,000-series index needs about 50 successful page requests
per refresh, plus bounded fallback/transport retries; fresh openings make zero
Komga list requests. Larger-than-cap libraries need manual links or a future
reviewed limit change; incomplete indexes never establish title uniqueness.

### Blue counter semantics

Both grid and list cards use the same blue book badge. Its tooltip identifies
stored Komga BOOKS, not verified individual chapters, with manual/automatic
provenance. Asterisk and accessible stale text identify old observations. Large
visible numbers use compact notation; the exact count remains in accessible text
and the tooltip. Unlinked/unknown counts are hidden; a linked known zero is valid.
A question mark indicates a link or candidate requiring review. Badge groups
wrap on narrow cards when Komga data is present, keeping existing counts visible. The badge follows
normal card navigation into manga details, where the Komga section provides
confirmation, correction, inventory and Open in Komga controls.

Grid-level summary requests deduplicate IDs and use sequential batches of at most
200. Cards only read React context. Summaries use bulk local database/cache reads;
they never call the per-manga inventory API. The provider observes only an active
refresh, bounded to 65 two-second waits, then leaves cards responsive with cached
state. No recurring idle polling occurs. Source/migration grids do not request
these summaries. Disabling counter display does not disable auto-linking.

Temporary failures retain eligible cached counts as stale and retain links.
Changed keys cannot reuse another credential's snapshot. Authentication/permission
failures hide cached counts. Missing/deleted series and changed destinations keep
links for correction but hide counts and require review. Automatic links with new
contradictory title identity evidence are flagged for review; they are never
silently replaced. No count changes a Suwayomi download flag or either service's
reading progress, and nothing writes Komga metadata or files.

M0071 adds preferences, link provenance and a persistent compact index; released
M0070 is unchanged. Portable user-settings backups include preferences, link
provenance and unlink opt-outs using optional protobuf fields; old backups keep
compatible defaults. Imported connections remain disabled and imported links
require reconfirmation. Existing manual decisions win on restore; backed-up manual decisions outrank
unconfirmed automatic links. The index is disposable
and excluded from portable backups; the secret is never included. Full database
backups retain it, partitioned by credential/destination. Sync restore still does
not import Komga settings or decisions. Single-user/no-login behavior is unchanged.

## Configure after reviewing this implementation

1. Confirm the installed Komga version supports API keys and the documented REST
   API. Create a key in Komga for the user whose progress you want displayed. Give
   that user access to the desired collection. Suwayomi applies no language or
   library restriction. An API key inherits its Komga user's permissions; the
   integration itself only calls read endpoints.
2. Put the key alone in a file **outside this checkout** on the NAS. Mount it read
   only into the **Suwayomi container**, not just Gluetun. The default path is
   `/run/secrets/komga_api_key`. `KOMGA_API_KEY_FILE` can select another container
   path. Do not put the key itself in Compose environment variables, URLs or Git.
   Ensure Suwayomi's container user can read the file. A trailing newline is fine.
3. In Settings → Tracking, save the **Internal Komga API URL** and optional
   **Browser-facing Komga URL**. Both are base URLs, including any reverse-proxy
   subpath, without `/api/v1`, credentials, query strings or fragments. Browser URL
   is used only for the Open in Komga link. Save and run **Test Connection** before
   enabling the integration. Testing is allowed while disabled.
4. Enable it, open a manga and choose **Link Komga series**. Search by title, inspect
   suggestions and explicitly confirm the correct series. Automatic linking is off
   unless you enable it or explicitly run the shared-index scan.
   Correct/unlink controls affect only this integration's link.

Illustrative additions to the existing Compose service (not a replacement Compose
file; retain its current image, volumes, user and `network_mode`):

```yaml
services:
  suwayomi:
    environment:
      KOMGA_API_KEY_FILE: /run/secrets/komga_api_key
    secrets:
      - komga_api_key

secrets:
  komga_api_key:
    file: /absolute/NAS/path/outside/repositories/komga-api-key.txt
```

A read-only bind mount at that path is also supported. Do not paste the actual key
into support output. The WebUI/GraphQL responses expose only a configured/not
configured indicator, never the key or its filesystem path. Remote error bodies
are discarded. HTTP redirects are rejected without forwarding the key. Prefer
HTTPS for traffic outside a trusted container network. Key rotation is picked up
without a restart and invalidates the credential-specific cache.

## Verify Docker connectivity on the NAS

**NAS access was not performed.** The supplied mapping is `8080:25600` and Komga's
container name is `komga`. Neither this mapping nor `network_mode: service:…`
proves that Suwayomi can resolve `komga` through Gluetun.

Before choosing an internal URL, the operator should confirm the actual service
namespace owner and network membership. These read-only commands intentionally
omit environment variables and credentials:

```sh
docker inspect --format '{{.HostConfig.NetworkMode}}' <suwayomi-container>
docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{$name}} {{end}}' <namespace-owner-container>
docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{$name}} {{end}}' komga
docker inspect --format '{{.Config.Image}}' komga
docker port komga 25600
```

Check Komga's About screen for the actual application version (an image tag such
as `latest` does not identify it). Confirm the complete `network_mode` value in
Compose; `service` alone is not the documented service-sharing syntax.

- `http://komga:25600` is a candidate only if name resolution, shared network
  membership and Gluetun's routing/firewall allow it from Suwayomi's namespace.
- `http://<NAS-LAN-IP>:8080` is a separate candidate using the published NAS port,
  subject to that namespace's LAN routing/firewall. `localhost:8080` in Suwayomi
  refers to its shared namespace, not automatically to the NAS host.
- A service using `network_mode: service:gluetun` shares Gluetun's network
  namespace. Do not add a conflicting `networks:` entry to Suwayomi. Any eventual
  network/firewall adjustment needs review of the actual Compose configuration.

Save the candidate and use **Test Connection**. This executes an authenticated
request from the actual Suwayomi server HTTP client, so it checks the relevant
DNS, routing, TLS, port and key together. Success says “Authenticated from the
Suwayomi server network context.” Failure distinguishes unreachable, rejected key,
forbidden access, redirects and missing/unsupported API routes. No client-side
proxy or browser-to-Komga connectivity is assumed. Confirm the separate browser
URL in a browser on the device that will use Open in Komga.

Outstanding installation details: exact Komga version, full network-mode owner,
actual Docker network names, routing/firewall permissions, final internal/browser
URLs and readable secret mount. No NAS configuration was changed.

## Comparison, progress and refresh behavior

Suwayomi download counts are **stored download flags**, not a new disk scan. Komga
book/read/unread/in-progress counts describe physical books, which may be volumes
or multi-chapter archives. The integration does not equate a book with a chapter.

An exact chapter match requires an explicit Komga book metadata link matching a
stored absolute source chapter URL with one unambiguous chapter number. Decimal
numbers are normalized; duplicate source editions are reported separately. Bare
Komga number metadata is shown as an unverified hint. Ranges, special labels,
missing links, unnumbered source chapters and truncated/changed pagination leave
missing counts **uncertain**, with unmatched numbers and download discrepancies
available for inspection. Most existing archives may lack those URL links; title
or filename resemblance alone does not establish an exact match. The integration
does not add links or metadata to Komga.

URL comparison preserves encoded path/query separators: `/a%2Fb` is distinct from
`/a/b`, and an encoded `&` is distinct from a query-parameter separator. Trailing
slashes are preserved too. Hostname case, percent-escape hex case and fragments
can differ without changing a match; path/query content is not decoded.

Manual links persist in the database across restarts and updates. Changing the
internal API destination retains links but requires manual reconfirmation to
avoid silently using a series ID from a different collection. Changing only the
browser URL or enabling/disabling does not invalidate confirmed links.

Inventory is fetched on demand for linked manga. Cache lifetime is 60 seconds,
with a manual refresh (minimum two seconds between successful fetch attempts) and
ten-second error backoff. Concurrent requests share one fetch; the integration
serializes its own requests independently of source/download queues. Cached local
download comparisons are recalculated from the database without another Komga
request. Temporary remote failures show the last good inventory as stale;
authentication/permission/missing-series failures hide it. There is no polling or
background full-library scan. Disabled/unlinked manga make no automatic Komga
requests. Explicit Test Connection and enabled manual search still make requests.

Fetching is bounded to ten pages of 200 books, a bounded response/cache size and a
time budget. Large or changing series can be incomplete; the UI labels that state
and does not claim a definitive missing count. Books are displayed in pages of 50.
The cache holds at most 16 snapshots and is discarded on restart.

Book requests use Komga's supported `url,asc` sorting on both GET and read-only
POST fallback routes. Collection is checked for duplicate IDs, wrong series IDs,
changing totals/page sizes and contradictory page metadata. An explicitly unsorted
response is rejected. Returned counts must agree before a complete inventory is
claimed; invalid responses cannot produce a definitive missing count. Optional
metadata may be absent on older API responses, but supplied metadata must agree.
Komga does not provide a transactional snapshot across these requests: concurrent
rescans or edits may require another refresh. API collation ties can also cause an
inconsistent page, which is rejected rather than silently accepted as complete.

The displayed book page resets when switching manga or confirming another series.
Search/dialog state is also reset when moving to another manga.

## Persistence, schema and compatibility

M0070 was unused and now adds only `komga_connection` and `komga_link`, with
user/manga foreign keys. No existing download/tracker table is repurposed. GraphQL
adds dedicated settings, search, manga inventory, link/unlink, connection-test and
refresh operations. Existing authentication and settings permissions are reused;
the current no-login default user can configure it normally.

Portable backups include non-secret connection information and manga links when
**user settings** and the relevant manga are included. The API-key file is never
included. Portable restore disables the imported connection and requires link
reconfirmation; it preserves any existing manually confirmed manga link. Older
backups remain compatible. Normal full-database persistence retains links without
reconfirmation; keep the external secret mount separately. Sync restoration does
not import connection settings or links.

Restoration first inserts absent decisions atomically. M0071 then conditionally
replaces only AUTOMATIC rows when importing a backed-up deliberate manual choice.
Existing manual links and opt-outs cannot be updated, including a link confirmed
concurrently during restore. A manual confirmation takes precedence whichever operation starts
first. H2 uses `MERGE` with only a `WHEN NOT MATCHED` clause; PostgreSQL uses
`INSERT ... ON CONFLICT DO NOTHING`. That earlier restore-race correction did not require a migration. M0071 now
adds the optional automatic-link and counter functionality described above. Concurrent H2 and isolated PostgreSQL 17.11 behavior are regression-tested;
the PostgreSQL validation includes 20 manual-confirmation/restoration races.

The client uses established GET series/book endpoints, falling back to documented
read-only POST list searches on 404/405, and current-user v2 → v1 fallback. It does
not require Komga's latest optional features. Versions without API-key support are
not supported; the installed version still needs confirmation. Mock API contracts
cover both routes, but a live Komga installation has not been tested.

References: [Komga API](https://komga.org/docs/openapi/komga-api/),
[Docker service network mode](https://docs.docker.com/reference/compose-file/services/#network_mode),
[Compose networking](https://docs.docker.com/compose/how-tos/networking/).

## Validation

See the current Phase 2 section in `CODEX_HANDOVER.md` for final regression counts,
commands and remaining environment limitations. All integration tests use mock
HTTP responses and disposable local data. No NAS or production Komga is contacted.

## Isolated PostgreSQL validation

The opt-in Windows/PowerShell 7 runner is `scripts/test-postgres.ps1`. Supply a
local PostgreSQL `bin` directory (containing `initdb.exe`, `pg_ctl.exe` and
`psql.exe`) and Java 21 via `JAVA_HOME`, then run:

```powershell
./scripts/test-postgres.ps1 -PostgresBin 'C:/tools/pgsql/bin'
```

It never accepts a database URL, existing cluster, Docker context or application
data directory. Each run creates an ACL-restricted, randomly named directory in
the current user's temporary directory and a new loopback-only PostgreSQL
cluster. Generated SCRAM credentials, ownership tokens, separate fresh/upgrade
databases and a non-superuser application role prevent accidental reuse of an
existing installation. Ambient PostgreSQL/Suwayomi/Java-option environment
variables are removed for the run and restored afterwards. The test JVM blocks
non-loopback sockets, uses private downloads/local-source directories and a
nonexistent Komga secret; Komga requests go only to local mock servers. No server
scheduler or downloader is started. The normal `:server:test` task stays on H2.

The upgrade uses the repository's frozen migrations through M0069, seeds
synthetic library/reading/download/adaptive/Completed data, then runs the normal
migration entry point through M0070 and compares every legacy table's rows.
The fresh database runs the full migration chain. Checks cover migration records,
disabled defaults, primary/foreign keys, nullability/length constraints and
cascades. Existing Komga service/GraphQL tests run against PostgreSQL, including
the 20-race manual-confirmation/backup-restoration regression. The runner compares
sorted snapshots of every application table across an actual PostgreSQL
stop/start, then reruns persistence checks in a new test JVM.

The cluster is stopped and plaintext credential files are removed in `finally`;
the stopped disposable cluster, snapshots, Gradle logs and per-phase JUnit XML
reports remain in the printed `suwayomi-pg-<random>` directory for review. If the
shell is forcibly killed, inspect that exact directory's ownership marker before
stopping its cluster with `pg_ctl -D <that-directory>/cluster -m fast -w stop`.
Do not point this command at another cluster. Delete only that disposable
directory after review. Java 21 is required for the test-only socket guard;
newer Java versions that remove SecurityManager fail closed before DB migration.
The runner does not install or modify a Windows PostgreSQL service.

See `CODEX_HANDOVER.md` for executed results and remaining limitations. A synthetic
upgrade is not a restore rehearsal of a real library, and a workstation database
version does not establish compatibility with an unconfirmed NAS version.

Validated on 19 October 2026 with PostgreSQL 17.11 and Java 21: **14 prepare-phase
tests and 1 restart-phase test passed**, with no failures, errors or skips. Both
databases' complete table snapshots matched across the PostgreSQL restart. The
20 concurrent manual-confirmation/restoration races passed using PostgreSQL's
`ON CONFLICT DO NOTHING`. The normal **452 server tests**, server formatting and
ktlint checks also passed. Generated credentials were absent from logs/reports,
plaintext credential files were removed, and all disposable clusters were stopped.
No production server change was necessary. NAS access and real-data restore
rehearsals remain outside this validation.

## Further validation stages

### Additional PostgreSQL rehearsals

1. Run the isolated harness with the intended PostgreSQL major version once that
   version is confirmed. Its synthetic M0069 baseline uses the frozen migration
   classes from this branch, rather than running a separate Phase 1 executable.
2. If a realistic library copy is needed later, use a consistent `pg_dump -Fc`
   archive and restore it into a separate empty disposable database with an
   explicit destination and `--no-owner --no-acl --exit-on-error`. Inspect its
   configuration/credentials offline and block external network access before
   starting its Suwayomi clone. Never restore over the live database. A live H2
   database is not input for pg_restore: use synthetic fixtures or an application
   backup imported only into disposable PostgreSQL.
3. Rollback consists of discarding the disposable environment or restoring its
   pre-migration copy. Do not downgrade an app against a migrated production DB or
   remove live migration/table records manually. Keep the existing installation
   untouched until the upgrade and restore rehearsals both pass.
References: [PostgreSQL pg_dump](https://www.postgresql.org/docs/current/app-pgdump.html)
and [pg_restore](https://www.postgresql.org/docs/current/app-pgrestore.html).

### NAS connectivity and real API behavior

1. First test a separate Komga instance with its own configuration/database and
   copies of a few sample archives. Mount sample content read only. Use the same
   Komga version as the NAS. Connect a disposable Suwayomi installation with
   synthetic manga; never attach the test Suwayomi process to the live data root,
   database or downloads. Check the normal, special/decimal, duplicate-title,
   volume and multi-page cases, plus reconnects, revoked keys and restart behavior.
2. Obtain the NAS Komga version, exact network-mode owner, actual Docker networks
   and URL/subpath information using the diagnostic steps above. Do not change
   Gluetun networks/firewall rules or assume `komga:25600` resolves. Determine the
   route from the namespace actually used by the test Suwayomi process. A failed
   network test is a reason to inspect configuration, not bypass the VPN/firewall.
3. Before any live API smoke test, verify independently restorable backups of
   the live Suwayomi configuration/database and Komga configuration/database.
   Retain download backups/snapshots. Use each database's supported consistent
   backup method, or a stopped-service copy; do not copy open database files and
   assume they form a valid backup.
4. Keep the disposable Suwayomi root and empty/private downloads. Mount a dedicated
   revocable API-key file outside Git and logs. A Komga key inherits its user's
   permissions; it is not inherently a read-only credential. For strongest
   protection, expose a temporary proxy that permits only GET current-user,
   series/detail/book-list endpoints and the two exact POST list-search endpoints,
   rejecting every other route/method. Keep the real key upstream of that proxy
   if practical. Do not grant the test container direct access to Komga's DB or
   content files. No Suwayomi login/account changes are required.
5. With Komga integration disabled, save the tested internal URL and separate
   browser URL, then run Test Connection. Enable it only in the disposable
   Suwayomi instance, manually link one synthetic manga and inspect inventory,
   uncertainty, cached failures and read/unread progress. Do not open a Komga reader
   or manually change either application's read/download state during this check.
   Compare progress and download baselines before/after; inspect only sanitized
   method/path/status request logs, never headers or keys. Do not scan or reorganize
   Komga's live content as part of the smoke test.
6. Stop the test instance/proxy, revoke the temporary key and retain the reports.
   A later production installation should use the exact reviewed commit/artifact,
   a tested rollback copy and the integration initially disabled. Installation,
   publishing an image and any NAS deployment require a separate user instruction.
