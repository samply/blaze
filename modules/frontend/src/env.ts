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

export const variables = defineEnvVars({
  BACKEND_BASE_URL: required,
  AUTH_CLIENT_ID: required,
  AUTH_CLIENT_SECRET: required,
  AUTH_ISSUER: required,
  AUTH_SECRET: required,
  // no longer supported, only read to warn about it
  ORIGIN: optional
});
