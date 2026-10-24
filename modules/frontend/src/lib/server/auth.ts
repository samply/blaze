import { betterAuth } from 'better-auth';
import { genericOAuth, keycloak } from 'better-auth/plugins/generic-oauth';
import { svelteKitHandler, sveltekitCookies } from 'better-auth/svelte-kit';
import type { Handle } from '@sveltejs/kit';
import { building, dev } from '$app/environment';
import { base } from '$app/paths';
import { getRequestEvent } from '$app/server';
import { env } from '$env/dynamic/private';
import { keycloakUser } from '$lib/server/session';

/**
 * Better Auth without a database: the session and the Keycloak tokens are
 * kept in encrypted cookies, so the frontend stays stateless.
 *
 * No base URL is configured. Better Auth takes the origin of each request
 * instead, which is the origin the adapter derived for it.
 */
export const auth = betterAuth({
  // keeps the callback URL at `/fhir/auth/callback/keycloak`
  basePath: `${base}/auth`,
  secret: env.AUTH_SECRET,
  advanced: { useSecureCookies: !dev },
  plugins: [
    genericOAuth({
      config: [
        {
          ...keycloak({
            clientId: env.AUTH_CLIENT_ID ?? '',
            clientSecret: env.AUTH_CLIENT_SECRET ?? '',
            issuer: env.AUTH_ISSUER ?? '',
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

/**
 * Answers the requests to the Better Auth endpoints under `basePath`.
 */
export const handle: Handle = ({ event, resolve }) =>
  svelteKitHandler({ event, resolve, auth, building });
