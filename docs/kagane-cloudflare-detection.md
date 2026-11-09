# Kagane image errors and Byparr 3.1.1

## Confirmed server defect

Previously, CloudflareInterceptor classified every HTTP 403/503 with
`Server: cloudflare` (or `cloudflare-nginx`) as an anti-bot challenge. Cloudflare
also proxies ordinary origin errors, including JSON image/token rejections.
Suwayomi then classified any solver message without `not detected` as a solved
challenge. A generic `Done` or `Success` therefore reached the mandatory
`cf_clearance` check and threw, even when no challenge had been served.

Detection now requires `cf-mitigated: challenge`, or a 403/503 Cloudflare HTML
response containing a known challenge marker within the first 64 KiB. JSON and
ordinary HTML rejections remain intact. The clearance check stays mandatory on
the challenge-solving path. When a solver explicitly reports no challenge and
provides neither a usable response fallback nor clearance, the original response
is returned without an unnecessary retry or cookie-check exception. The original
body stays open until returned or replaced, and is closed on failure.

## Kagane attribution and interceptor ordering

Current upstream Kagane retries image URLs containing a token after 401/403/507;
403 HTML instead produces an explicit network-blocking error. Although Suwayomi
initially registers Cloudflare before source-added interceptors, upstream KeiSource
explicitly moves Cloudflare below source-specific interceptors. The supplied stack
trace confirms extension interceptors outside Cloudflare in the installed source.
Consequently, the old exception can prevent Kagane's refresh handler from receiving
the rejected response. Regression coverage exercises refresh in both orders.
The supplied BASIC-level log does not include the original response Content-Type
or body, so the exact image rejection still needs confirmation after deployment.

## History

Local repository history attributes the broad status/Server detection to
`f47dc6b9de` (4 December 2022). Generic solver-message detection is present in
`344ef0dc43` (25 July 2026). Commit `57f2340302` (16 August 2026, upstream #2277)
replaced the solver's 2xx-status success criterion with the clearance-cookie
criterion. These changes predate the latest personal updates. A recent extension,
host, or Byparr change may expose this older interaction; the evidence does not
identify a particular recent update as the trigger. The clearance criterion is
appropriate for an actual clearance-based bypass; classification is the defect.

Opening an image URL directly and receiving `{"error": -7}` proves an application
error body was returned in that request, not its meaning or the response to the
extension's request. An expired token, missing browser context, and a rejected
refreshed token still need differentiation. No extension defect is established.

## Byparr context

The official v3.1.1 `LinkRequest` has no headers/Origin/Referer input. Its endpoint
uses `page.goto(request.url)` without forwarding source headers. Adding a headers
field to Suwayomi's solver JSON would not fix that version: Byparr ignores it.
Sending those headers on the POST to `/v1` also does not forward them to the image
host. This context gap can make a direct browser navigation differ from the
extension's image request. The patch avoids sending ordinary image rejections to
that navigation in the first place.

That version also sets `solution.status` to 200 rather than the actual navigation
status; a successful solver API response is not proof the image host accepted the
request. No Byparr or deployment configuration was changed.

Sources inspected:

- https://github.com/ThePhaseless/Byparr/blob/v3.1.1/src/models.py
- https://github.com/ThePhaseless/Byparr/blob/v3.1.1/src/endpoints.py
- https://github.com/keiyoushi/extensions-source/blob/main/src/all/kagane/src/eu/kanade/tachiyomi/extension/all/kagane/Kagane.kt
- https://github.com/keiyoushi/extensions-source/blob/main/core/src/main/kotlin/keiyoushi/source/KeiSource.kt
- https://github.com/keiyoushi/extensions-source/blob/main/src/all/mangaball/src/eu/kanade/tachiyomi/extension/all/mangaball/MangaBall.kt

## Remaining remote verification

No running Suwayomi/Byparr service was contacted. On the deployed patched server,
retry with a freshly obtained chapter image URL and compare the original image
status, Content-Type, cf-mitigated header and body against the response after token
refresh. Keep tokens and cookies out of shared logs. Confirm the real request
contains the extension's Origin and any Referer it supplies. A non-challenge JSON
rejection should no longer trigger Byparr or CloudflareBypassException. If image
loading still fails, investigate that rejection and refresh result separately.

Manga Ball's current upstream source returns chapter image URLs and has no Kagane
token-refresh interceptor. The same detection fix applies to ordinary Cloudflare-
proxied errors, but no actual Manga Ball failure response was provided, so its
cause is not verified and it is not claimed fixed.

## Local validation

Seven CloudflareInterceptor regression tests passed, including an OkHttp request
with a simulated token rejection and successful refresh in either interceptor
order. The solver's generic success without clearance still throws on the genuine
challenge path. Server ktlintCheck and git diff --check passed. Gradle ran offline
using an existing temporary Java 21 JDK; no software was installed or network
settings changed. These checks do not establish live Kagane download success.
