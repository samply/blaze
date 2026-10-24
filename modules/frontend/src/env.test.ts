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
  const required = [
    'BACKEND_BASE_URL',
    'AUTH_CLIENT_ID',
    'AUTH_CLIENT_SECRET',
    'AUTH_ISSUER',
    'AUTH_SECRET'
  ] as const;

  // A missing value has to stop the app at startup rather than fail requests
  // later, and an empty AUTH_SECRET would let Better Auth fall back to its
  // default secret.
  it.each(required)('requires %s to be set', (name) => {
    expect(validate(name, undefined)).toHaveProperty('issues');
    expect(validate(name, '')).toHaveProperty('issues');
  });

  it.each(required)('accepts a value for %s', (name) => {
    expect(validate(name, 'value')).toStrictEqual({ value: 'value' });
  });

  it('lets ORIGIN unset', () => {
    expect(validate('ORIGIN', undefined)).toStrictEqual({ value: '' });
  });
});
