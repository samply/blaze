import { resolve } from '$app/paths';

export const signInPath = resolve('/__sign-in');

/**
 * Builds the sign-in URL, remembering `target` as the page to return to
 * after signing in.
 */
export function signInUrl(target: URL | undefined): string {
  if (target === undefined) {
    return signInPath;
  }

  return `${signInPath}?redirect=${encodeURIComponent(target.pathname + target.search)}`;
}

/**
 * A 401 response is how the server signals that the session has expired
 * (RFC 6750 section 3). See `canRedirectToSignIn` in `hooks.server.ts` for
 * the other half of this contract.
 */
export function sessionExpired(res: Pick<Response, 'status'>): boolean {
  return res.status === 401;
}
