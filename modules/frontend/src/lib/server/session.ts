/**
 * An access token as handed out by Better Auth, with its expiry if known.
 */
export interface AccessToken {
  accessToken?: string;
  accessTokenExpiresAt?: Date | string;
}

/**
 * Selects the Keycloak account kept in the account cookie of the request.
 */
type AccountRequest = { body: { useAccountCookie: true }; headers: Headers };

/**
 * The part of the Better Auth API needed to authenticate a request.
 */
export interface AuthApi {
  getSession(input: { headers: Headers }): Promise<{ user: { name: string } } | null>;
  getAccessToken(input: AccountRequest): Promise<AccessToken>;
  refreshToken(input: AccountRequest): Promise<AccessToken>;
}

/**
 * The signed-in user together with the access token sent to the backend.
 */
export interface Session {
  user: { name: string };
  accessToken: string;
}

/**
 * The time an access token has to be valid at least, so that it doesn't
 * expire while the backend requests of a page are still running.
 */
const minValidity = 10000;

/**
 * Returns the access token of `token` if it is valid for at least
 * `minValidity`.
 */
function usableAccessToken({ accessToken, accessTokenExpiresAt }: AccessToken): string | undefined {
  if (
    accessTokenExpiresAt !== undefined &&
    new Date(accessTokenExpiresAt).getTime() - Date.now() >= minValidity
  ) {
    return accessToken;
  }
}

/**
 * Returns an access token that is valid for at least `minValidity`.
 *
 * Better Auth refreshes an access token itself only if it expires in less
 * than 5 s, so one expiring a few seconds later is refreshed here.
 */
async function validAccessToken(api: AuthApi, headers: Headers): Promise<string | undefined> {
  const request: AccountRequest = { body: { useAccountCookie: true }, headers };
  return (
    usableAccessToken(await api.getAccessToken(request)) ??
    usableAccessToken(await api.refreshToken(request))
  );
}

/**
 * Authenticates the request with `headers`, returning its session or
 * nothing if the user has to sign in.
 *
 * Without a database, Better Auth keeps the Keycloak tokens in the account
 * cookie and refreshes an expired access token itself. If that fails, for
 * example because the refresh token expired as well, there is no session.
 */
export async function authenticate(api: AuthApi, headers: Headers): Promise<Session | undefined> {
  const session = await api.getSession({ headers });

  if (!session) {
    return undefined;
  }

  try {
    const accessToken = await validAccessToken(api, headers);
    return accessToken ? { user: { name: session.user.name }, accessToken } : undefined;
  } catch {
    return undefined;
  }
}

/**
 * The claims of a Keycloak profile the user is built from.
 */
export interface KeycloakProfile {
  sub?: string | number | null;
  email?: string | null;
  name?: string | null;
  preferred_username?: unknown;
}

/**
 * Maps a Keycloak profile to the Better Auth user.
 *
 * Better Auth refuses to sign in a user without an email, but Keycloak users
 * don't need one, so an email is made up from the subject. The reserved
 * `.invalid` TLD ensures it can never reach anyone.
 */
export function keycloakUser(profile: KeycloakProfile): { email: string; name: string } {
  return {
    email: profile.email ?? `${profile.sub}@keycloak.invalid`,
    name: profile.name ?? String(profile.preferred_username ?? '')
  };
}
