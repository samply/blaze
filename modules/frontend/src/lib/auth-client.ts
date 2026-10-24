import { createAuthClient } from 'better-auth/client';
import { base } from '$app/paths';

/**
 * Created on use, in the browser, where the client takes its origin from the
 * page.
 */
function authClient() {
  return createAuthClient({ basePath: `${base}/auth` });
}

/**
 * Signs in with Keycloak, returning to `callbackURL` afterwards.
 *
 * On success the browser is redirected to Keycloak, so only an error is
 * returned.
 */
export async function signIn(callbackURL: string): Promise<string | undefined> {
  const { error } = await authClient().signIn.social({ provider: 'keycloak', callbackURL });
  return error?.message;
}

/**
 * Ends the session of this app.
 */
export async function signOut(): Promise<void> {
  await authClient().signOut();
}
