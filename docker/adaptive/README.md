# NAS adaptive-update test image

GitHub Actions builds the server and the modified `webui/` source from the same
commit, runs validation, smoke-tests the Linux container, then publishes:

```text
ghcr.io/chronosmage-alt/suwayomi-server-personal:adaptive-test
ghcr.io/chronosmage-alt/suwayomi-server-personal:sha-<12-character-commit>
```

The SHA tag is intended to retain each build; the workflow never deliberately
overwrites one from a different commit. For strict reproducibility, use the digest
printed in the successful workflow summary, since re-running the same source commit
can resolve a newer upstream runtime. The exact upstream runtime digest is recorded
there too. Neither this workflow nor this image deploys to the NAS.

## Running the build

Pushes affecting the server, UI or container build on
`investigate/adaptive-library-updates` trigger the workflow. This permits the initial
test build without merging into master. `workflow_dispatch` is also configured;
GitHub only makes manual dispatch available once the workflow is on the default
branch. Existing upstream release workflows are unchanged.

An initial GHCR package is private by default. In GitHub, open the
`suwayomi-server-personal` package settings and make it public to allow a normal
unauthenticated NAS pull. Alternatively, authenticate Docker on the NAS with a
classic GitHub token having `read:packages`. Do not put a token in Compose or this
repository. A repository's public visibility does not itself make a new package public.

## UGREEN DXP4800 Pro

1. Create the `data` subfolder in your `Vol2Apps/SuwaDevTest` shared-folder location.
2. Copy `compose.yaml` beside that subfolder. It pulls a prebuilt amd64 image; no
   Dockerfile, source upload or NAS build is required.
3. Ensure `data` is writable by the configured UID/GID. Defaults are 1000:1000;
   set `PUID` and `PGID` in a `.env` beside Compose if your NAS user differs.
4. Prefer replacing `:adaptive-test` in Compose with the exact `:sha-...` tag or
   digest from the completed workflow. This prevents an unnoticed version change.
5. Start only this project in UGREEN Docker, or from SSH in that folder:

```sh
docker compose pull
docker compose up -d
docker compose logs --tail=100 suwayomi
```

Open `http://YOUR-NAS-IP:4568`. There is no login in this fresh test configuration.
Nothing mounts your production Suwayomi or Komga directories. They still share NAS
resources and the public IP; avoid parallel Comix refresh experiments.

The image installs its included modified UI at startup. Keep flavor CUSTOM and
WebUI update interval zero. Source cooldowns and download behavior are unchanged.
`UPDATE_INTERVAL: "0"` disables automatic checks at each startup. To test automatic
scheduling, change that value to e.g. `"12"` and recreate the test container, then
enable the adaptive master switch and chosen sources in the Library settings.

## Migration test using a copied database

First establish that the fresh test instance starts correctly. This template uses
H2. Verify the production database type before importing anything.

1. Stop the test project with `docker compose stop`.
2. Briefly stop production Suwayomi for a consistent H2 copy, or use a consistent
   offline snapshot. Never copy a live database file or point two servers at it.
3. Save the fresh test database separately. Copy production's `database.mv.db`
   into this project's `data` folder, leaving the original untouched. Ensure the
   copied file is writable by the configured test UID/GID.
4. Keep the test instance's fresh server.conf. Do not copy production download,
   local-source, backup, sync or integration paths/settings into the test instance.
5. Restart production, then start only this test project and inspect migration logs.

Older H2 engine migrations or other baseline database upgrades may also run; this
is not solely the adaptive feature's additive migration. Imported library settings
can include per-user download settings. Keep the timer disabled and install no
remote extensions until those settings are reviewed. Database references do not
make production downloads available in this separate test directory.

For PostgreSQL, do not use the H2 import procedure. Restore a dump into a separate
test database/service and configure a dedicated connection; never reuse the live
database/schema or PostgreSQL data directory.

## Stop or roll back the test

```sh
docker compose down
```

This keeps `data` intact and does not stop production or Komga. To retry with an
older test image, restore its matching test-data snapshot too. Do not downgrade
an image against an already migrated database as an assumed rollback. Production
cutover remains a separate, unapproved operation.
