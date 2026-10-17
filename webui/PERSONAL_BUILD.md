# Personal Suwayomi WebUI

This directory contains the matching WebUI source for this server's adaptive
library updates. It was copied from Suwayomi-WebUI release v20260929.01, commit
`41443eaba44bcbdf45d666fe27471b50d41fd994` (r3518), including the reviewed local
adaptive controls, skipped-update explanations and personal-server backup flag
compatibility changes. The upstream MPL-2.0 license is retained in `LICENSE`.

The source is maintained in this repository so a container build cannot accidentally
use an upstream UI without the feature. No node_modules, local settings, Git
credentials, compiled UI or separate repository history are included. The original
WebUI repository remains unchanged by this import.

Build and validation commands are in `.github/workflows/adaptive-container.yml`.
The container keeps the base revision file r3518 for server version compatibility;
the OCI source-revision label and SHA image tag identify the actual combined build.
