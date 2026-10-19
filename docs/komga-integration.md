# Optional Komga library integration

The integration starts disabled. It reads inventory and the Komga API-key user's
reading progress alongside existing trackers. It does not update Komga, reading
progress, Suwayomi download flags, files, source scheduling or update queues. No
manga-source requests are made. Existing no-login installations continue to use
Suwayomi's default user; there is no new account-management screen.

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
   suggestions and explicitly confirm the correct series. Nothing is auto-linked.
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

Restored manga links use one atomic insert-if-absent database statement. They
cannot update an existing link, including one confirmed concurrently during a
backup restore. A manual confirmation takes precedence whichever operation starts
first. H2 uses `MERGE` with only a `WHEN NOT MATCHED` clause; PostgreSQL uses
`INSERT ... ON CONFLICT DO NOTHING`. No migration change is needed for this
correction. Concurrent H2 behavior is regression-tested; a live PostgreSQL instance
was not exercised locally.

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

## Safest next validation stages (not executed)

### PostgreSQL migration and concurrency

1. Use a disposable PostgreSQL instance on a workstation, matching the intended
   PostgreSQL major version. Give it a new volume, database and dedicated user
   whose credentials cannot access production. Give Suwayomi a separate temporary
   root and no production download/configuration mounts. Keep background updates,
   downloads, sync and external tracker activity disabled; do not copy a live
   server configuration or its credentials into this environment.
2. Check a fresh installation through M0070. Separately create a pre-M0070 database
   using the Phase 1 parent `b952d64e` in an isolated checkout/runtime and seed
   synthetic manga, chapters, read/bookmark/download flags, adaptive history and
   Completed rules. Save a baseline database export before starting Phase 2 against
   that disposable database. This checks the actual M0069 → M0070 upgrade, rather
   than only creating empty Komga tables.
3. Confirm that migration 70 is recorded once; both new tables, composite primary
   key and cascading foreign keys match the models. Compare existing application
   rows/flags with the baseline, excluding genuinely volatile timestamps/logs.
   Confirm Komga starts disabled and secrets are absent from the database.
4. Add an opt-in PostgreSQL test harness; the existing default ApplicationTest
   setup uses H2. Run the Komga GraphQL/client/inventory/backup tests against
   PostgreSQL as well as H2. Exercise simultaneous manual confirmation and backup restoration using
   separate database connections; assert the manual link wins and SQL uses
   `ON CONFLICT DO NOTHING`. Check FK cleanup, portable backup flags and persistence
   across restarts, all using disposable records.
5. If a realistic PostgreSQL library copy is subsequently needed, use a consistent
   `pg_dump -Fc` archive and restore it with `pg_restore` into a separate empty
   disposable database, with an explicit destination and `--no-owner --no-acl
   --exit-on-error`. Inspect configuration/credentials offline and block external
   network access before starting its Suwayomi clone. Never restore over the live
   database. A live H2 database is not input for pg_restore: use synthetic fixtures
   or an application backup imported only into the disposable PostgreSQL server.
6. Rollback consists of discarding the disposable environment or restoring its
   pre-migration copy. Do not downgrade an app against a migrated production DB or
   remove live migration/table records manually. Keep the existing installation
   untouched until the upgrade rehearsal and restore rehearsal both pass.

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
