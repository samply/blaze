# Frontend Implementation

## Authentication / Authorization

The frontend uses the [Auth.js][1] [@auth/sveltekit][2] library for for authentication and authorization. 

* a single Keycloak provider is used
* the env vars `AUTH_CLIENT_ID`, `AUTH_CLIENT_SECRET`, `AUTH_ISSUER` and `AUTH_SECRET` are used as config
* the authorization code flow is used
* at sign-in the access token and refresh token are stored in a secure, HTTP only, encrypted JWT session cookie
  * nobody can access the tokens in the session cookie, because it is encrypted and only the server-side of the frontend has the secret
* the session cookie is transferred for every request (the frontend is stateless)
* the access token will be refreshed via the refresh token if possible
* the session will expire at the same time as the last successful refreshed access token will expire 

## Loading

All loading happens server side. There are `+page.server.ts` / `+layout.server.ts` load functions and form actions, and
no `+server.ts` endpoints at all.

* The browser never makes a plain `fetch` to this app. It only issues document loads, SvelteKit's own `__data.json`
  data requests and `use:enhance`d form submissions.
* Backend requests are made with the event's `fetch`. The access token stays on the server: `handleFetch` in
  `src/hooks.server.ts` adds it and rewrites the request URL from this app's `ORIGIN` to `BACKEND_BASE_URL`.
* Every request that reaches the app is therefore one that the browser or SvelteKit's client runtime turns into a
  navigation on its own when it is answered with a redirect.

Because of that, a `redirect` thrown in `handleAuthorization` reaches the user as a navigation regardless of how the
request was made. SvelteKit puts it into the right envelope by itself:

| Kind of request                | Envelope                                                              |
|--------------------------------|-----------------------------------------------------------------------|
| document load                  | a plain 307 response, which the browser follows                       |
| `__data.json` data request     | `{ type: 'redirect' }`, which the client router turns into a `goto`   |
| `use:enhance`d form submission | a redirect `ActionResult`, which `applyAction` turns into a navigation |

### Backend URLs

Backend request URLs are built by prefixing `base` from `$app/paths`, for example
`` `${base}/${params.type}/${params.id}` ``. `resolve` from `$app/paths` is for links and navigation targets —
`redirect(303, …)` after a form action, `href` — and must not be used for a backend request made from a **load**.

The two look interchangeable, because the frontend is mounted under the same path as the backend's FHIR base, but they
are not. With SvelteKit's default `paths.relative`, `resolve` returns a path relative to the page being rendered and
computes its depth from the URL the browser is at. For a `__data.json` request that URL has one segment more than the
`event.url` the event's `fetch` resolves a relative URL against, so a `resolve`d backend URL silently loses the base
path on every client-side navigation.

A **form action** is the one place where a `resolve`d backend URL is still correct: it is posted to the page's own URL,
which never carries the extra `__data.json` segment, so the relative path resolves to the same depth the load would
have used. The terminology actions rely on that.

### Streamed load data

A promise returned un-awaited from `load` and consumed with `{#await ...}{:catch}` is streamed. Its rejection reaches
the `{:catch}` block as the jsonified error *body* — SvelteKit runs it through `handle_error_and_jsonify` — not as the
`HttpError`, so the status is not available the way it is for an error that fails the load itself. `App.Error`
therefore carries an optional `status`, which the `appError` functions in `src/routes/[type=type]/util.ts` and
`src/lib/metadata.ts` set. Every `error(…)` body that can surface in such a block has to carry it — that includes the
StructureDefinition loads made while transforming a streamed bundle and the one `handleFetch` throws.

[1]: <https://authjs.dev>
[2]: <https://www.npmjs.com/package/@auth/sveltekit>
