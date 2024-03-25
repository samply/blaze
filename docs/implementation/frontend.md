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

[1]: <https://authjs.dev>
[2]: <https://www.npmjs.com/package/@auth/sveltekit>
