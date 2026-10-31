/**
 * The variables Better Auth is configured with.
 */
export interface AuthEnv {
  AUTH_CLIENT_ID: string;
  AUTH_CLIENT_SECRET: string;
  AUTH_ISSUER: string;
  AUTH_SECRET: string;
}

/**
 * The config of Better Auth.
 */
export interface AuthConfig {
  clientId: string;
  clientSecret: string;
  issuer: string;
  secret: string;
}

/**
 * Returns the config of Better Auth from `env`.
 *
 * Fails, naming the empty variables, if one of them is empty, so that the
 * app stops at startup rather than failing requests later. An empty
 * `AUTH_SECRET` would let Better Auth fall back to its default secret.
 *
 * The check lives here and not in the schemas of `src/env.ts`, because the
 * variables are only required if auth is enabled and a schema only sees its
 * own variable.
 */
export function authConfig(env: AuthEnv): AuthConfig {
  const empty = Object.entries(env)
    .filter(([, value]) => !value)
    .map(([name]) => name);

  if (empty.length > 0) {
    const verb = empty.length === 1 ? 'has' : 'have';
    throw new Error(
      `${empty.join(', ')} ${verb} to be set and not empty, unless ENABLE_AUTH is false.`
    );
  }

  return {
    clientId: env.AUTH_CLIENT_ID,
    clientSecret: env.AUTH_CLIENT_SECRET,
    issuer: env.AUTH_ISSUER,
    secret: env.AUTH_SECRET
  };
}
