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

## Session Expiry

When the session expires, the user has to end up on the sign-in page and be returned to the page they came from
afterwards — no matter which kind of request happened to discover the expiry.

### Why this cannot be handled on the server alone

Every browser request reaches the frontend's Node server, and `handleAuthorization` in `src/hooks.server.ts` sees the session cookie on all of them. The server can therefore always *detect* an expired session. What it cannot always do is *act* on it.

On a client-side navigation a universal load (`+page.ts`) runs **in the browser** and fetches with a plain `fetch()`. A `fetch()` follows redirects transparently, so answering it with a 307 to the sign-in page means the load receives that page's HTML with status 200, calls `res.json()` on it, and throws a `SyntaxError`.

For those requests the server answers `401` instead (RFC 6750, section 3) and the client turns it into a navigation. **The server decides, the client acts.**

Which of the two applies is decided by one predicate, `canRedirectToSignIn`:

```text
!isSubRequest && (isDataRequest || acceptsHtmlDocument || isFormActionRequest)
```

A redirect is only sent where the browser or SvelteKit's client runtime turns it into a navigation by itself: document loads, SvelteKit's own `__data.json` requests, and `use:enhance`d form submissions.

### Where expiry is detected

| Checkpoint                                    | Location                        | Sees                                      | Timing                                     |
|-----------------------------------------------|---------------------------------|-------------------------------------------|--------------------------------------------|
| `handleAuthorization`                         | `src/hooks.server.ts`           | our own session cookie                    | before routing and before any backend call |
| `handleFetch`                                 | `src/hooks.server.ts`           | the backend's verdict on our access token | after the backend response, server side    |
| `ensureSignedIn` / `redirectIfSessionExpired` | `src/lib/sign-in-navigation.ts` | the 401 that reached the browser          | after the response, client side            |

There is deliberately no client-side check *before* a request. An expired access token does not imply a 401: reading the session in `handleAuthorization` renews it first, and only a failed renewal produces one. Whether a renewal would succeed is not something the client can know, so the expiry is not published to it.

### Which mechanism to use

| Situation                              | Example                                             | Use                                    | On expiry                                                                                        |
|----------------------------------------|-----------------------------------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------|
| Awaited value in a universal load      | `routes/[type=type]/[id=id]/+page.ts`               | `fetchJson`                            | throws `redirect`; SvelteKit turns the load's rejection into a navigation                        |
| Streamed value consumed by `{#await}`  | `routes/[type=type]/util.ts`                        | `fetchJsonStreamed`                    | navigates with `goto`, then returns a promise that never settles so the `{#await}` stays pending |
| `fetch` from a component event handler | `routes/[type=type]/search-form.svelte`             | `fetchJsonStreamed`                    | same as above                                                                                    |
| Server load (`+page.server.ts`)        | `routes/__admin/jobs/+page.server.ts`               | nothing                                | the `__data.json` request is a data request, so the hook redirects it                            |
| Form action (`use:enhance`)            | `routes/CodeSystem/[id=id]/$lookup/+page.server.ts` | nothing                                | the hook redirects it; `applyAction` navigates                                                   |
| `+server.ts` proxy route               | `routes/[type=type]/+server.ts`                     | nothing — forward the backend's status | the client sees the 401 and handles it                                                           |
| A load's sub-request during SSR        | `routes/[type=type]/+page.ts` while rendering       | nothing                                | `handleFetch` redirects, using `event.url` as the return-to target                               |

Two rules follow from the table:

* **Never call `fetch` directly in browser code.** `fetchJson` and
  `fetchJsonStreamed` also set a non-HTML `Accept` header, which is what makes
  `acceptsHtmlDocument` classify the request as a plain fetch. A bare
  `fetch(url)` sends `Accept: */*`, would be classified as a document load, and would receive a redirect it then follows
  into HTML.
* **Choosing `fetchJson` where `fetchJsonStreamed` belongs fails silently.**
  The `redirect` is thrown, nothing navigates, and the `{:catch}` block renders an error instead.

### What changed with #4082

| Aspect                  | Before                                                                     | Now                                                   |
|-------------------------|----------------------------------------------------------------------------|-------------------------------------------------------|
| Unauthenticated request | always answered with a 307, whatever made it                               | 307 only where it becomes a navigation, otherwise 401 |
| Client-side navigation  | followed the 307, parsed HTML as JSON, showed a 500                        | 401 is turned into a navigation to the sign-in page   |
| Recovery                | reload the page by hand                                                    | automatic                                             |
| Return-to target        | `?redirect=${event.url}`, unencoded, so the query was lost                 | built by `signInUrl`, percent-encoded                 |
| Backend 401 during SSR  | surfaced from the load, without a return-to target                         | `handleFetch` redirects with `event.url`              |
| Fetching in a load      | `fetch` + `if (!res.ok) error(...)` + `res.json()`, repeated per call site | `fetchJson` / `fetchJsonStreamed`                     |

[1]: <https://authjs.dev>
[2]: <https://www.npmjs.com/package/@auth/sveltekit>
