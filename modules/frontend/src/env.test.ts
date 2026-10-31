import { describe, it, expect } from 'vitest';
import { variables } from './env.js';

/**
 * Validates `value` with the schema of the variable `name` the way SvelteKit
 * does at startup.
 */
function validate(name: keyof typeof variables, value: string | undefined) {
  return variables[name].schema['~standard'].validate(value);
}

describe('variables test', () => {
  // A missing value has to stop the app at startup rather than fail requests
  // later.
  it('requires BACKEND_BASE_URL to be set', () => {
    expect(validate('BACKEND_BASE_URL', undefined)).toHaveProperty('issues');
    expect(validate('BACKEND_BASE_URL', '')).toHaveProperty('issues');
  });

  it('accepts a value for BACKEND_BASE_URL', () => {
    expect(validate('BACKEND_BASE_URL', 'value')).toStrictEqual({ value: 'value' });
  });

  // The auth variables are only needed if auth is enabled, which `authConfig`
  // checks, because a schema only sees its own variable.
  const auth = ['AUTH_CLIENT_ID', 'AUTH_CLIENT_SECRET', 'AUTH_ISSUER', 'AUTH_SECRET'] as const;

  it.each(auth)('lets %s unset', (name) => {
    expect(validate(name, undefined)).toStrictEqual({ value: '' });
  });

  it.each(auth)('accepts a value for %s', (name) => {
    expect(validate(name, 'value')).toStrictEqual({ value: 'value' });
  });

  it('enables auth by default', () => {
    expect(validate('ENABLE_AUTH', undefined)).toStrictEqual({ value: true });
    expect(validate('ENABLE_AUTH', '')).toStrictEqual({ value: true });
  });

  it.each([
    ['true', true],
    ['false', false]
  ])('accepts %s for ENABLE_AUTH', (value, expected) => {
    expect(validate('ENABLE_AUTH', value)).toStrictEqual({ value: expected });
  });

  // A typo must not silently enable or disable auth.
  it.each(['TRUE', 'no', '0'])('rejects %s for ENABLE_AUTH', (value) => {
    expect(validate('ENABLE_AUTH', value)).toHaveProperty('issues');
  });

  // The layout hides the admin section and the user menu without auth.
  it('makes ENABLE_AUTH public', () => {
    expect(variables.ENABLE_AUTH.public).toBe(true);
  });

  it('lets ORIGIN unset', () => {
    expect(validate('ORIGIN', undefined)).toStrictEqual({ value: '' });
  });
});
