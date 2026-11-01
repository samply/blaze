import { defineEnvVars } from '@sveltejs/kit/env';

/**
 * Requires a variable to be set and not empty, so that a missing value stops
 * the app at startup rather than failing requests later.
 *
 * `vite build` validates the variables as well. There, the values of `.env`
 * are used, which aren't bundled, because all variables are read at runtime.
 */
const required = {
  schema: (input: string | undefined) => {
    if (!input) {
      throw new Error('has to be set and not empty');
    }
    return input;
  }
};

/**
 * Falls back to the empty string for an unset variable.
 */
const optional = { schema: (input: string | undefined) => input ?? '' };

/**
 * Accepts `true` and `false`, falling back to `true` for an unset variable.
 */
const enabledByDefault = {
  schema: (input: string | undefined) => {
    if (!input || input === 'true') {
      return true;
    }
    if (input === 'false') {
      return false;
    }
    throw new Error('has to be true or false');
  }
};

export const variables = defineEnvVars({
  BACKEND_BASE_URL: required,
  // public, because the layout hides the admin section and the user menu without auth
  ENABLE_AUTH: { ...enabledByDefault, public: true },
  // only required if auth is enabled, which `authConfig` checks, because a
  // schema only sees its own variable
  AUTH_CLIENT_ID: optional,
  AUTH_CLIENT_SECRET: optional,
  AUTH_ISSUER: optional,
  AUTH_SECRET: optional,
  // no longer supported, only read to warn about it
  ORIGIN: optional
});
