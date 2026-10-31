import { betterAuth } from 'better-auth';
import { genericOAuth, keycloak } from 'better-auth/plugins/generic-oauth';
import { svelteKitHandler, sveltekitCookies } from 'better-auth/svelte-kit';
import type { Handle } from '@sveltejs/kit/hooks';
import { building, dev } from '$app/env';
import { resolve } from '$app/paths';
import { getRequestEvent } from '$app/server';
import type { AuthConfig } from '#lib/server/auth-config.js';
import { keycloakUser } from '#lib/server/session.js';

/**
 * Creates Better Auth without a database: the session and the Keycloak tokens
 * are kept in encrypted cookies, so the frontend stays stateless.
 *
 * No base URL is configured. Better Auth takes the origin of each request
 * instead, which is the origin the adapter derived for it.
 */
export function createAuth({ clientId, clientSecret, issuer, secret }: AuthConfig) {
  return betterAuth({
    // keeps the callback URL at `/fhir/auth/callback/keycloak`
    basePath: resolve('auth'),
    secret,
    advanced: { useSecureCookies: !dev },
    plugins: [
      genericOAuth({
        config: [
          {
            ...keycloak({
              clientId,
              clientSecret,
              issuer,
              // signing out only ends the session of this app, not the one at Keycloak
              disableProviderLogout: true
            }),
            mapProfileToUser: keycloakUser
          }
        ]
      }),
      // has to be the last plugin
      sveltekitCookies(getRequestEvent)
    ]
  });
}

export type Auth = ReturnType<typeof createAuth>;

/**
 * Answers the requests to the Better Auth endpoints under `basePath`.
 */
export function authHandle(auth: Auth): Handle {
  return ({ event, resolve }) => svelteKitHandler({ event, resolve, auth, building });
}
