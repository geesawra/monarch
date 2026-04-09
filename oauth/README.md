# Monarch OAuth client metadata

`client-metadata.json` is the file Bluesky's OAuth authorization server fetches
during the OAuth flow to identify Monarch as a client. **Its hosted URL is the
client_id** — the field inside the file must match the URL it's served from
exactly, or the auth server will reject the request.

## Hosting requirements

- **Public HTTPS** — Bluesky servers must be able to fetch it from the open
  internet. No localhost, no private networks, no GitHub raw URLs that 302 to a
  different host.
- **Stable URL** — once users have authorized Monarch, the `client_id` becomes
  a permanent identifier. Changing it forces every user to re-authorize and
  invalidates all existing refresh tokens.
- **Correct `Content-Type`** — serve as `application/json`.
- **HTTPS cert chain trusted by Bluesky's HTTP client** (any normal Let's
  Encrypt cert works).

## Before deploying

Edit the JSON and replace every `monarch.geesawra.industries` reference with
your actual hosting domain. The fields you need to update:

- `client_id` — the URL the file is served from
- `client_uri` — landing page for the project
- `logo_uri` — square PNG, shown on the consent screen
- `tos_uri` — terms of service
- `policy_uri` — privacy policy

The other fields (`redirect_uris`, `application_type`, `dpop_bound_access_tokens`,
`scope`, etc.) are protocol requirements and should not be changed without
matching code edits in `Bluesky.kt` / `AndroidManifest.xml`.

### Note on `redirect_uris`

The redirect URI uses a reverse-DNS custom scheme
(`industries.geesawra.monarch:/oauth/callback`) per RFC 8252 §7.1. Bluesky's
OAuth server enforces two non-obvious rules strictly, and you'll trip over both
if you wing it:

1. **Scheme must be reverse-DNS form** based on a domain you control. Generic
   schemes like `monarch://` get rejected with:
   > URL must use the "https:" or "http:" protocol, or a private-use URI
   > scheme (RFC 8252) at body.redirect_uri

2. **Form is `<scheme>:/{path}` — single slash, no authority/host.** RFC 8252
   §7.1 shows `com.example.app:/oauth2redirect/example-provider` as the
   canonical example. Using `<scheme>://...` (the more familiar URL-with-host
   form) gets rejected with:
   > Private-Use URI Scheme must be in the form <scheme>:/{path} (notice the
   > single slash!) as per RFC 8252

If you fork Monarch and change the application ID, you have to update the
redirect URI in **four** places to match: `client-metadata.json`, the
`OAUTH_REDIRECT_URI` constant in `Bluesky.kt`, the `<intent-filter>`'s
`android:scheme` in `AndroidManifest.xml`, and the deep-link guard inside
`MainActivity.kt`. Android intent-filters can't honor `android:path` without
an `android:host`, so we filter by scheme alone in the manifest and validate
the path inside `MainActivity.LaunchedEffect`.

## After deploying

Update the `OAUTH_CLIENT_ID` constant in
`app/src/main/java/industries/geesawra/monarch/datalayer/Bluesky.kt` to match
the URL you deployed to. The Android code reads this constant when initiating
OAuth flows and includes it in the authorization request — it must match the
`client_id` field inside the hosted JSON exactly.

## Verifying

Once hosted, fetch it yourself:

```
curl -i https://your-domain.example/client-metadata.json
```

Confirm:
- HTTP 200
- `Content-Type: application/json`
- The `client_id` field inside the body matches the URL you fetched it from
- The `redirect_uris` field contains exactly `monarch://oauth/callback`
