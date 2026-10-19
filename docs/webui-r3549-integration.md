# Bundled WebUI r3549 integration

Phase 1, approved 19 October 2026. Development stays on
`dev`, the permanent custom-server and bundled-WebUI development branch. No upstream server changes, Komga work,
extension edits, publication or NAS deployment are included.

## Reproducible upstream comparison

- Original ancestor: official r3518 / v20260929.01,
  `41443eaba44bcbdf45d666fe27471b50d41fd994`.
- Pinned target: official r3549,
  `8ab2f3385d643f36a415bc8df0c414f86c8a97b1`.
- Personal starting revision: `639355fbad51cd663ab619731453d7b0a08d361c`.

The 31-commit upstream delta was compared file by file against both the ancestor
and personal bundle. Unchanged upstream files and personal-only files were left
in place. Overlapping text changes used three-way merging. Both sides of the
English catalogue conflicts were retained. Upstream generated GraphQL files were
excluded from this merge so the custom server schema remains authoritative.
Upstream repository publishing workflows and hooks were not imported.

## Integrated behavior

- Source-language selection crash fix.
- Correct Markdown handling for descriptions and notes; remove obsolete `marked`.
- Centralized application initialization, startup retry, authentication and
  subscription management, initial subpage routing and proactive token refresh.
- Explicit default-category identity, selector display and dialog sizing fixes.
- Single-entry migration uses the bulk flow; migration back-navigation cleanup.
- Mobile collapsed navigation, desktop two-pane settings and reader fixes.
- Desktop card context-menu behavior.
- Optional new-chapter notifications, disabled by default.

Custom adaptive settings, per-title modes, historical diagnostics, skipped-update
fields, Completed controls, language grouping/filtering and tests are retained.
The library settings merge includes both custom controls and notifications.
The theme merge retains personal styling and adds upstream dialog height rules.
The lockfile retains the existing personal test dependencies.

## Integration fixes beyond the pinned upstream source

Permanent subscription registration now calls the restart callback and unregisters
it when its consumer unsubscribes. This prevents stale callbacks accumulating
when initialization restarts. Initialization also clears stopped subscriptions,
shares cancellation across retries, retries synchronous failures, caps retry delay
and prevents a stopped startup from continuing into new background work.

The notification observer ignores incomplete updater cache data. Stopped or
disabled checks discard their results before advancing the notification watermark
or displaying notifications. Notifications consume updater state and stored chapter
queries only: they do not start updates, touch queues, write chapter state or
invoke extensions. Adaptive skips remain skips; no new chapters means no alert.
Initial chapter imports are filtered using the upstream stored-history heuristic.

Notifications use existing server metadata preferences, not a SQL migration.
Native browser notifications require browser support/permission and generally a
secure context. Foreground notifications use toasts. Cross-tab deduplication uses
Web Locks where available; simultaneous devices can still race. The upstream
notifier bounds searches to ten pages of fifty and summarizes large result sets;
it is an update hint rather than a durable notification delivery queue.

## Compatibility and packaging

The target expects at least server r2366, including `CategoryType.isDefaultCategory`.
The current server base r2422 already supplies it. The refresh mutation name in
the schema is unchanged; only its client operation name changes.
No migrations, server runtime changes, tracker-provider changes or authentication
requirements are introduced. No-login operation continues using existing user 1.
M0068 and M0069 remain unchanged.

The existing custom Docker workflow is preserved; its bundled revision marker is
updated to r3549. The server's generic official-WebUI download pin remains r3518,
because it is not the build source for the CUSTOM container. Do not run the generic
download task to replace the personal bundle. UI auto-update stays disabled in the
personal container setup.

## Validation

All requested local checks passed:

| Check | Result |
| --- | --- |
| Full server regression suite | 428 passed, no failures/errors/skips |
| Server ktlint | Passed |
| WebUI tests | 39 passed in ten files |
| TypeScript | Passed after schema regeneration |
| WebUI lint | Passed |
| Full WebUI formatting | Passed |
| English catalogue extraction | Passed, custom strings retained |
| Production Vite/PWA build | Passed |
| Git whitespace/conflict-marker review | Passed |

The 18 new frontend regressions cover initializer retry/cancellation and cleanup,
no-login and token-refresh decisions, subscription restart registration, responsive
settings routing, MAL/AniList card preservation, migration back-handler cleanup,
disabled notifications, incomplete/running updater status, skipped-only results,
stored new chapters, initial imports and disabling during an in-flight check.
Existing tests continue covering language filtering/grouping, historical displays
and Completed controls. The unchanged full server suite covers provenance,
scheduling, manual category removals, backup behavior and tracker/API contracts.

The server suite's schema-export test supplied the input to normal GraphQL
codegen. All three outputs were regenerated and formatted; two remained identical,
and `graphql.ts` gained the expected category fields and renamed refresh operation.

Headless Chrome inspected the built UI against a fresh, separate no-login server
at desktop 1440x1000 and mobile 390x844 sizes. Direct settings startup, desktop
default-pane navigation, mobile menu navigation, adaptive/Completed/source controls
and tracker settings passed without horizontal overflow or uncaught browser errors.
Both screenshot layouts were inspected. Closing both ends of the test proxy's TCP
connection caused all four permanent subscriptions to reconnect and receive data.
Notifications were enabled/disabled against that disposable database. An explicitly
verified empty library update exercised updater events without manga source calls.

Server tests use isolated H2 storage. Frontend fixtures do not contact live tracker
providers or manga sources. Production databases/downloads and the extension
repository were untouched. No real source migration, live tracker authorization,
Docker image build, Linux/Java 25 CI run or NAS deployment was performed. Native
notification delivery across real devices remains a later environment check.

The pinned dependency combination retains a legacy-minifier fallback warning and
large-chunk warnings; the build succeeds. Existing upstream server-update notices
can suggest an official preview on the custom fork; QA dismissed the notice and
did not download it.

The newest `CODEX_HANDOVER.md` section records tooling, the successful compiler
heap setting and the location of local browser reports/screenshots.
