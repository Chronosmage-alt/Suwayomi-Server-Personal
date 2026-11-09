# Kagane / Manga Ball dev diagnostics

This dev build adds `ImageHostDiagnostics` records for `kstatic.to`,
`kagane.moe`, `kagane.org`, `mangaball.com`, and
`bulbasaur.poke-black-and-white.net`. Enable the existing server Debug logs
setting, retry one failed chapter from each source, and collect:

```sh
docker logs --since 5m suwayomi-vpn-1 2>&1 | grep ImageHostDiagnostics
```

The existing container must be updated to the new tested `adaptive-test`
image first. Publication does not update or restart the NAS deployment.

Records share an `id` for the OkHttp call. Redirects and retries can produce
multiple request/response records for the same ID; use their timestamps,
paths and response Location to follow each exchange.

- `phase=request`: headers actually sent, captured by OkHttp's
  `requestHeadersEnd` callback after extension header interceptors run.
  Includes Origin, Referer, User-Agent, Accept, encodings, fetch metadata,
  client hints and cache headers. Cookie names and authorization presence
  are recorded, never their credential values.
- `phase=connection`: destination peer, HTTP protocol, TLS version and Java
  proxy type. A VPN container may correctly report `DIRECT`: a tunnel in
  the container/network namespace is not an explicit Java HTTP proxy.
  The peer is the remote server/edge, not the container's public exit IP.
- `phase=response`: status, Server, Cloudflare mitigation/ray/cache headers,
  CORS/cache headers, encoding, redirect Location and Set-Cookie names.
  Error responses also include a bounded readable body sample.

Sampling happens at the network layer, before source-specific interceptors
can close a rejection or throw a generic error. It peeks at most 16 KiB of
wire data, decodes at most 8 KiB with existing gzip/deflate/Brotli/Zstd
dependencies, strips HTML style/script markup and emits at most 2,048 characters of
sanitized text. Binary bodies are omitted. Successful image bodies are not
sampled. Decoding failures are reported by exception type and do not replace
the original response. Reading a small error prefix can add diagnostic
latency while Debug logs are enabled.

All URL query values, URL user information and fragments are redacted.
Known request credentials echoed in error text, common credential fields,
embedded URL query values and long opaque strings are also redacted. The
ordinary BASIC HTTP logger additionally redacts common token/signature
query parameters, which previously appeared in download logs. JWT expiry
metadata is labeled **unverified** and does not establish signature validity
or whether the service accepts that token.

These diagnostics cover Suwayomi's OkHttp requests, not the separate native
CEF browser or Byparr's internal network stack. They do not solve challenges,
change cookies, rewrite image URLs, retry downloads, or alter the earlier
Cloudflare-detection fix.

Look for a readable rejection message/error code, `cf-mitigated`, missing or
unexpected Origin/Referer, a surprising User-Agent change, redirects, and
cache status. A Cloudflare Server header alone does not establish who made
the rejection. Neither a 403 nor `DIRECT` alone proves a VPN/IP block.

The diagnostic records omit credential values, but can contain chapter paths,
server peer addresses and site error text. Share the filtered records rather
than an unreviewed full server log or raw DevTools export.
