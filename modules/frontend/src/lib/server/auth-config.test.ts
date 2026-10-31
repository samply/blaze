import { describe, it, expect } from 'vitest';
import { authConfig } from '#lib/server/auth-config.js';

const env = {
  AUTH_CLIENT_ID: 'client-id',
  AUTH_CLIENT_SECRET: 'client-secret',
  AUTH_ISSUER: 'https://keycloak.localhost/realms/blaze',
  AUTH_SECRET: 'secret'
};

describe('authConfig test', () => {
  it('returns the config', () => {
    expect(authConfig(env)).toStrictEqual({
      clientId: 'client-id',
      clientSecret: 'client-secret',
      issuer: 'https://keycloak.localhost/realms/blaze',
      secret: 'secret'
    });
  });

  // A missing value has to stop the app at startup rather than fail requests
  // later, and an empty AUTH_SECRET would let Better Auth fall back to its
  // default secret.
  it.each(Object.keys(env))('fails if %s is empty', (name) => {
    expect(() => authConfig({ ...env, [name]: '' })).toThrow(
      `${name} has to be set and not empty, unless ENABLE_AUTH is false.`
    );
  });

  it('names all empty variables', () => {
    expect(() => authConfig({ ...env, AUTH_CLIENT_ID: '', AUTH_SECRET: '' })).toThrow(
      'AUTH_CLIENT_ID, AUTH_SECRET have to be set and not empty, unless ENABLE_AUTH is false.'
    );
  });
});
