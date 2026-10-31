# Frontend Implementation

## Authentication / Authorization

The frontend uses the [Better Auth][1] library for authentication and authorization, in its stateless mode without a
database.

* a single Keycloak provider is used, configured in `src/lib/server/auth.ts`
* the env vars `AUTH_CLIENT_ID`, `AUTH_CLIENT_SECRET`, `AUTH_ISSUER` and `AUTH_SECRET` are used as config
* the authorization code flow is used, with the callback URL `/fhir/auth/callback/keycloak`
* at sign-in, three secure, HTTP only cookies are set, each standing in for a database table of Better Auth:

  | Cookie                                | Format                                       | Content                                                          |
  |---------------------------------------|----------------------------------------------|------------------------------------------------------------------|
  | `__Secure-better-auth.session_token`  | random session ID with an HMAC signature     | the ID of the session                                            |
  | `__Secure-better-auth.session_data`   | JWE (encrypted JWT)                          | the session and the user                                         |
  | `__Secure-better-auth.account_data.*` | JWE (encrypted JWT), split into chunks       | the access token, the refresh token and the ID token of Keycloak |

  * nobody can access the tokens in the account cookie, because it is encrypted and only the server-side of the
    frontend has the secret (`AUTH_SECRET`)
  * together the cookies are about 6 KB, most of it the Keycloak tokens, so a reverse proxy has to accept request
    headers of that size
  * the cookies of Auth.js, used before Better Auth, are expired on every page response, because together with the
    Better Auth cookies they would exceed that size
* the cookies are transferred for every request (the frontend is stateless)
* the access token will be refreshed via the refresh token if possible, at the latest 10 s before it expires, so that it
  doesn't expire while the backend requests of a page are still running
* if the access token can't be refreshed, the user has to sign in again
* no base URL is configured; Better Auth takes the origin of each request, which is the one the adapter derived for it
* Keycloak users don't need an email, but Better Auth requires one, so an email under the reserved `.invalid` TLD is
  made up for users without one

### Without Auth

With the env var `ENABLE_AUTH` set to `false`, the frontend can be deployed without login, for example as a demo
server.

* Better Auth isn't created at all, so the `AUTH_*` env vars aren't needed; `authConfig` in
  `src/lib/server/auth-config.ts` checks them only if auth is enabled, because the schemas in `src/env.ts` only see
  their own variable
* `handleFetch` sends the backend requests without an access token and passes a 401 of the backend through, because
  there is no sign-in page to redirect to
* the admin section is removed, because everybody could use it: `rejectAuthOnlyRoutes` in `src/hooks.server.ts`
  answers the admin routes and the sign-in page with 404. That check lives in the `handle` hook rather than in the
  admin layout, because a form action runs before any load.
* `ENABLE_AUTH` is a public env var, so that the layout can hide the **Admin** navigation item and the user menu

## Loading

All loading happens server side. There are `+page.server.ts` / `+layout.server.ts` load functions and form actions, and
no `+server.ts` endpoints at all.

* Apart from signing in and out, which go to the Better Auth endpoints under `/fhir/auth` and never reach a load, the
  browser never makes a plain `fetch` to this app. It only issues document loads, SvelteKit's own `__data.json` data
  requests and `use:enhance`d form submissions.
* Backend requests are made with the event's `fetch`. The access token stays on the server: `handleFetch` in
  `src/hooks.server.ts` adds it and rewrites the request URL from the origin of the page request to `BACKEND_BASE_URL`.
* Every request that reaches the app is therefore one that the browser or SvelteKit's client runtime turns into a
  navigation on its own when it is answered with a redirect.

Because of that, a `redirect` thrown in `handleAuthorization` reaches the user as a navigation regardless of how the
request was made. SvelteKit puts it into the right envelope by itself:

| Kind of request                | Envelope                                                              |
|--------------------------------|-----------------------------------------------------------------------|
| document load                  | a plain 307 response, which the browser follows                       |
| `__data.json` data request     | `{ type: 'redirect' }`, which the client router turns into a `goto`   |
| `use:enhance`d form submission | a redirect `ActionResult`, which `applyAction` turns into a navigation |

### Rejected access tokens

`handleAuthorization` only knows that Better Auth handed out an access token which isn't expired. The backend can still
reject that access token — revoked at the identity provider, clock skew, a realm mismatch — and
answers 401. `handleFetch` redirects to the sign-in page in that case, for the same reason `handleAuthorization` can: 
the request it was serving is one SvelteKit turns into a navigation.

The recovery lives in the hook rather than in the load that made the request, because `event.url` — the page the 
user is on, and so the return-to target — is not something a load can read. A 403 is passed through instead:
there the token was accepted, and the user simply may not do this, which signing in again does not change.

### Backend URLs

Backend request URLs are built by `backendUrl` from `src/lib/backend.ts`, for example
`` backendUrl(`/${params.type}/${params.id}`) `` or `backendUrl('/Patient', url.searchParams)`. It is the only place
that reads the base path. `resolve` from `$app/paths` is for links and navigation targets — `redirect(303, …)` after a
form action, `href` — and must not be used for a backend request.

The two look interchangeable, because the frontend is mounted under the same path as the backend's FHIR base, but they
are built from different inputs and are not always equal, so a call site should say which of the two it means. Two
concrete reasons `resolve` cannot serve a backend URL:

* Some backend paths are not routes of this app at all — there is none for `/$totals` or
  `/__admin/Task/{id}/$pause`. That the generated `Pathname` type admits them anyway is an accident of the
  `[type=type]` routes widening it to `/${string}`.
* With SvelteKit's default `paths.relative`, `resolve` returns a path relative to the page being rendered and computes
  its depth from the URL the browser is at. For a `__data.json` request that URL has one segment more than the
  `event.url` the event's `fetch` resolves a relative URL against, so a `resolve`d backend URL silently loses the base
  path on every client-side navigation.

The second reason does not apply to a **form action**, which is posted to the page's own URL and so never carries the
extra `__data.json` segment. The terminology actions used to rely on that; they use `backendUrl` now as well, because
the distinction is what makes a call site readable, not the depth arithmetic.

### Streamed load data

A promise returned un-awaited from `load` and consumed with `{#await ...}{:catch}` is streamed. Its rejection reaches
the `{:catch}` block as the jsonified error *body* — SvelteKit runs it through `handle_error_and_jsonify` — not as the
`HttpError`, so the status is not available the way it is for an error that fails the load itself. `App.Error`
therefore carries an optional `status`, which the `appError` functions in `src/routes/[type=type]/util.ts` and
`src/lib/metadata.ts` set. Every `error(…)` body that can surface in such a block has to carry it — that includes the
StructureDefinition loads made while transforming a streamed bundle and the one `handleFetch` throws.

Both streamed loads — `src/routes/[type=type]/+page.server.ts` and
`src/routes/[type=type]/__page/[pageId=pageId]/+page.server.ts` — await `fetchSearchMetadata` before they return the
streamed promise. That ordering is what lets a rejected access token still reach the sign-in page: a `redirect` thrown
after `load` has returned cannot become a navigation, because SvelteKit has already committed to the response and
serializes the rejection into the streamed chunk, where it surfaces as a generic 500. `fetchSearchMetadata` hits
`/metadata`, which Blaze protects with the same `wrap-auth-guard` as everything else
(`modules/rest-api/src/blaze/rest_api/routes.clj`, applied at the root `""` level), so `handleFetch` sees the 401 while
a redirect can still be acted upon.

The StructureDefinition loads are the exception: they run inside the streamed promise, via `transformBundle`. A token
revoked between the `/metadata` call and them ends in an error card rather than the sign-in page.

[1]: <https://www.better-auth.com>
