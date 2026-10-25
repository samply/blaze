import { describe, it, expect, vi } from 'vitest';
import { authenticate, keycloakUser, type AccessToken, type AuthApi } from '#lib/server/session.js';

interface TokenOptions {
  accessToken?: string;
  /** milliseconds until the access token expires, `null` for an unknown expiry */
  expiresIn?: number | null;
}

function tokens({ accessToken = 'token', expiresIn = 60000 }: TokenOptions): AccessToken {
  return {
    accessToken,
    accessTokenExpiresAt: expiresIn === null ? undefined : new Date(Date.now() + expiresIn)
  };
}

function api(
  options: {
    name?: string;
    token?: TokenOptions | Error;
    refreshedToken?: TokenOptions | Error;
  } = {}
) {
  function answer(token: TokenOptions | Error | undefined) {
    return async () => {
      if (token instanceof Error) {
        throw token;
      }
      return tokens(token ?? {});
    };
  }

  return {
    getSession: vi.fn(async () =>
      options.name === undefined ? null : { user: { name: options.name } }
    ),
    getAccessToken: vi.fn(answer(options.token)),
    refreshToken: vi.fn(answer(options.refreshedToken))
  } satisfies AuthApi;
}

describe('authenticate test', () => {
  const headers = new Headers({ cookie: 'session' });

  it('returns nothing without a session', async () => {
    const authApi = api();

    expect(await authenticate(authApi, headers)).toBeUndefined();
    expect(authApi.getAccessToken).not.toHaveBeenCalled();
  });

  it('returns the user and the access token of a session', async () => {
    const authApi = api({ name: 'John', token: { accessToken: 'token-1' } });

    expect(await authenticate(authApi, headers)).toStrictEqual({
      user: { name: 'John' },
      accessToken: 'token-1'
    });
  });

  // There is no database: the access token is kept in the account cookie.
  it('reads the access token from the account cookie of the request', async () => {
    const authApi = api({ name: 'John' });

    await authenticate(authApi, headers);

    expect(authApi.getSession).toHaveBeenCalledWith({ headers });
    expect(authApi.getAccessToken).toHaveBeenCalledWith({
      body: { useAccountCookie: true },
      headers
    });
  });

  // An expired access token is refreshed by Better Auth. If that fails, for
  // example because the refresh token expired as well, the user has to sign
  // in again.
  it('returns nothing if no valid access token can be obtained', async () => {
    const authApi = api({ name: 'John', token: new Error('refresh failed') });

    expect(await authenticate(authApi, headers)).toBeUndefined();
  });

  it('keeps an access token with more than 10 s left', async () => {
    const authApi = api({ name: 'John', token: { accessToken: 'token-1', expiresIn: 11000 } });

    expect((await authenticate(authApi, headers))?.accessToken).toBe('token-1');
    expect(authApi.refreshToken).not.toHaveBeenCalled();
  });

  // Better Auth only refreshes an access token with less than 5 s left. One
  // with a few seconds more is handed out unchanged and may expire while the
  // backend requests of the page are still running.
  it('refreshes an access token with less than 10 s left', async () => {
    const authApi = api({
      name: 'John',
      token: { accessToken: 'token-1', expiresIn: 6000 },
      refreshedToken: { accessToken: 'token-2' }
    });

    expect((await authenticate(authApi, headers))?.accessToken).toBe('token-2');
    expect(authApi.refreshToken).toHaveBeenCalledWith({
      body: { useAccountCookie: true },
      headers
    });
  });

  it('refreshes an access token without expiry', async () => {
    const authApi = api({
      name: 'John',
      token: { accessToken: 'token-1', expiresIn: null },
      refreshedToken: { accessToken: 'token-2' }
    });

    expect((await authenticate(authApi, headers))?.accessToken).toBe('token-2');
  });

  it('returns nothing if an access token with less than 10 s left cannot be refreshed', async () => {
    const authApi = api({
      name: 'John',
      token: { expiresIn: 6000 },
      refreshedToken: new Error('refresh failed')
    });

    expect(await authenticate(authApi, headers)).toBeUndefined();
  });

  it('returns nothing if the refresh returns no access token', async () => {
    const authApi = api({ name: 'John', token: { expiresIn: 6000 } });
    authApi.refreshToken.mockResolvedValue({
      accessToken: undefined,
      accessTokenExpiresAt: undefined
    });

    expect(await authenticate(authApi, headers)).toBeUndefined();
  });

  it('returns nothing if the refreshed access token has less than 10 s left as well', async () => {
    const authApi = api({
      name: 'John',
      token: { expiresIn: 6000 },
      refreshedToken: { expiresIn: 6000 }
    });

    expect(await authenticate(authApi, headers)).toBeUndefined();
  });
});

describe('keycloakUser test', () => {
  it('keeps the email and name of the profile', () => {
    expect(
      keycloakUser({ sub: 'id-0', email: 'john@example.com', name: 'John Doe' })
    ).toStrictEqual({
      email: 'john@example.com',
      name: 'John Doe'
    });
  });

  // Better Auth refuses to sign in a user without an email, but Keycloak
  // users don't need one.
  it('makes up an email from the subject if there is none', () => {
    expect(keycloakUser({ sub: 'id-0', name: 'John Doe' })).toStrictEqual({
      email: 'id-0@keycloak.invalid',
      name: 'John Doe'
    });
  });

  it('falls back to the username if there is no name', () => {
    expect(keycloakUser({ sub: 'id-0', preferred_username: 'john' })).toStrictEqual({
      email: 'id-0@keycloak.invalid',
      name: 'john'
    });
  });
});
