# Personal Suwayomi WebUI

This directory contains the matching WebUI source for this personal server.
Its original import was Suwayomi-WebUI v20260929.01, commit
`41443eaba44bcbdf45d666fe27471b50d41fd994` (r3518). Phase 1 integrates the upstream
delta through pinned commit `8ab2f3385d643f36a415bc8df0c414f86c8a97b1` (r3549)
using that original import as the three-way comparison ancestor. Custom adaptive
controls, historical provenance/diagnostics, Completed-category automation,
source-language filtering and backup compatibility remain part of this bundle.
The upstream MPL-2.0 license is retained in `LICENSE`.

The source is maintained in this repository so a container build cannot accidentally
use an upstream UI without the feature. No node_modules, local settings, Git
credentials, compiled UI or separate repository history are included. The original
WebUI repository remains unchanged by this import.

Build and validation commands are in `.github/workflows/adaptive-container.yml`.
The container records upstream baseline r3549 in its revision file; the OCI
source-revision label and SHA image tag identify the actual combined build.
The server's generic official-WebUI download pin is independent of this CUSTOM
bundle and has not been changed. See `docs/webui-r3549-integration.md` at the
repository root for integration details and validation.
