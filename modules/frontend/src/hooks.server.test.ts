import { describe, it, expect, vi } from 'vitest';
import { isRedirect } from '@sveltejs/kit';

vi.mock('$app/env/private', () => ({
  BACKEND_BASE_URL: 'http://backend:8080',
  ORIGIN: 'https://blaze.example.com'
}));
const authApi = vi.hoisted(() => ({
  getSession: vi.fn(),
  getAccessToken: vi.fn(),
  refreshToken: vi.fn()
}));
vi.mock('#lib/server/auth.js', () => ({
  auth: { api: authApi },
  handle: (input: { event: unknown; resolve: unknown }) =>
    (input.resolve as (e: unknown) => unknown)(input.event)
}));

const { checkOrigin, expireAuthJsCookies, handleAuthorization, handleFetch, init } =
  await import('./hooks.server.js');

/**
 * Lets the mocked Better Auth API answer with a session of the user `name`
 * holding `accessToken`, or with no session at all for `null`.
 */
function mockSession(session: { name: string; accessToken?: string | Error } | null) {
  authApi.getSession.mockResolvedValue(session ? { user: { name: session.name } } : null);
  authApi.getAccessToken.mockImplementation(async () => {
    if (session?.accessToken instanceof Error) {
      throw session.accessToken;
    }
    return {
      accessToken: session?.accessToken ?? 'token',
      accessTokenExpiresAt: new Date(Date.now() + 60000)
    };
  });
}

function mockEvent(options: {
  routeId?: string | null;
  session?: { name: string; accessToken?: string | Error } | null;
  url?: string;
}) {
  mockSession(options.session ?? null);
  const url = options.url ?? 'http://localhost/Patient';
  return {
    route: { id: options.routeId ?? '/[type=type]' },
    url: new URL(url),
    request: new Request(url),
    locals: {} as { session?: unknown }
  };
}

async function callHandleAuthorization(event: ReturnType<typeof mockEvent>) {
  const resolve = vi.fn().mockResolvedValue(new Response('ok'));
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const result = await handleAuthorization({ event: event as any, resolve } as any);
  return { result, resolve };
}

describe('init test', () => {
  it('warns if ORIGIN is set', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});

    await init();

    expect(warn).toHaveBeenCalledWith(expect.stringContaining('ORIGIN is no longer supported'));
    warn.mockRestore();
  });
});

describe('checkOrigin test', () => {
  it('logs an error if the origin of a request differs from ORIGIN and resolves', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    const event = { url: new URL('https://frontend:3000/fhir/Patient') };
    const resolve = vi.fn().mockResolvedValue(new Response('ok'));

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const result = await checkOrigin({ event: event as any, resolve } as any);

    expect(error).toHaveBeenCalledWith(expect.stringContaining('https://frontend:3000'));
    expect(resolve).toHaveBeenCalledWith(event);
    expect(await result.text()).toBe('ok');
    error.mockRestore();
  });
});

describe('expireAuthJsCookies test', () => {
  function cookieEvent(names: string[]) {
    return {
      cookies: {
        getAll: () => names.map((name) => ({ name, value: 'value' })),
        delete: vi.fn()
      }
    };
  }

  async function callExpireAuthJsCookies(event: ReturnType<typeof cookieEvent>) {
    const resolve = vi.fn().mockResolvedValue(new Response('ok'));
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const result = await expireAuthJsCookies({ event: event as any, resolve } as any);
    return { result, resolve };
  }

  // Browsers signed in before the switch to Better Auth still send the
  // cookies of Auth.js. Its session cookie alone is 3-4 KB, so together with
  // the Better Auth cookies, the Cookie header would exceed the 8 KB a reverse
  // proxy like nginx accepts by default.
  it('expires the cookies of Auth.js', async () => {
    const event = cookieEvent([
      '__Secure-authjs.session-token.0',
      '__Secure-authjs.session-token.1',
      '__Secure-authjs.callback-url',
      '__Host-authjs.csrf-token',
      'authjs.session-token'
    ]);

    await callExpireAuthJsCookies(event);

    expect(event.cookies.delete.mock.calls).toStrictEqual([
      ['__Secure-authjs.session-token.0', { path: '/' }],
      ['__Secure-authjs.session-token.1', { path: '/' }],
      ['__Secure-authjs.callback-url', { path: '/' }],
      ['__Host-authjs.csrf-token', { path: '/' }],
      ['authjs.session-token', { path: '/' }]
    ]);
  });

  it('keeps all other cookies', async () => {
    const event = cookieEvent([
      '__Secure-better-auth.session_token',
      '__Secure-better-auth.account_data.0',
      'other'
    ]);

    await callExpireAuthJsCookies(event);

    expect(event.cookies.delete).not.toHaveBeenCalled();
  });

  it('resolves the request', async () => {
    const event = cookieEvent(['__Secure-authjs.session-token']);

    const { result, resolve } = await callExpireAuthJsCookies(event);

    expect(resolve).toHaveBeenCalledWith(event);
    expect(await result.text()).toBe('ok');
  });
});

describe('handleAuthorization test', () => {
  it('bypasses authorization for the sign-in route itself', async () => {
    const event = mockEvent({ routeId: '/__sign-in', session: null });

    const { result, resolve } = await callHandleAuthorization(event);

    expect(resolve).toHaveBeenCalledWith(event);
    expect(await result.text()).toBe('ok');
  });

  it('sets locals.session and resolves normally for a valid session', async () => {
    const event = mockEvent({ session: { name: 'John', accessToken: 'token-1' } });

    const { resolve } = await callHandleAuthorization(event);

    expect(event.locals.session).toStrictEqual({ user: { name: 'John' }, accessToken: 'token-1' });
    expect(resolve).toHaveBeenCalledWith(event);
  });

  it('redirects to the sign-in page when there is no session', async () => {
    const event = mockEvent({ session: null });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
      expect((e as { location: string }).location).toBe('/fhir/__sign-in?redirect=%2FPatient');
    }
  });

  // The redirect target has to survive being read back out of the sign-in
  // page's query string, which an unencoded URL does not: its own `&` ends
  // the `redirect` param, truncating the search at its first parameter.
  it('keeps the query of the requested page in the redirect target', async () => {
    const event = mockEvent({
      session: null,
      url: 'http://localhost/Patient?_count=50&_summary=true'
    });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      const location = (e as { location: string }).location;

      expect(location).toBe('/fhir/__sign-in?redirect=%2FPatient%3F_count%3D50%26_summary%3Dtrue');

      // What the sign-in page actually does with it.
      expect(new URL(location, 'http://localhost').searchParams.get('redirect')).toBe(
        '/Patient?_count=50&_summary=true'
      );
    }
  });

  it('redirects to the sign-in page when no valid access token can be obtained', async () => {
    const event = mockEvent({
      session: { name: 'John', accessToken: new Error('refresh failed') }
    });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
    }
  });
});

function fetchEvent(options: { accessToken?: string; url?: string }) {
  return {
    url: new URL(options.url ?? 'http://localhost/fhir/Patient'),
    locals: { session: options.accessToken ? { accessToken: options.accessToken } : undefined }
  };
}

async function callHandleFetch(
  event: ReturnType<typeof fetchEvent>,
  response: Response,
  requestUrl = 'http://localhost/fhir/Patient'
) {
  const fetch = vi.fn().mockResolvedValue(response);
  const result = await handleFetch({
    request: new Request(requestUrl),
    fetch,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    event: event as any
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any);
  return { result, fetch };
}

describe('handleFetch test', () => {
  it('sends the request to the backend with the access token', async () => {
    const event = fetchEvent({ accessToken: 'token' });

    const { fetch } = await callHandleFetch(event, new Response('ok'));

    const request = fetch.mock.calls[0][0] as Request;
    expect(request.url).toBe('http://backend:8080/fhir/Patient');
    expect(request.headers.get('Authorization')).toBe('Bearer token');
  });

  // The origin of the backend request is the one the adapter derived for the
  // page request, so swapping exactly that origin keeps the two in step.
  it('swaps the origin of the page request for the backend base URL', async () => {
    const event = fetchEvent({
      accessToken: 'token',
      url: 'https://blaze.example.com/fhir/Patient'
    });

    const { fetch } = await callHandleFetch(
      event,
      new Response('ok'),
      'https://blaze.example.com/fhir/Patient?_count=50'
    );

    const request = fetch.mock.calls[0][0] as Request;
    expect(request.url).toBe('http://backend:8080/fhir/Patient?_count=50');
  });

  // Only the origin at the start of the URL is the one of this app. One
  // further on, for example in a query param, has to stay as it is.
  it('swaps only the origin at the start of the URL', async () => {
    const event = fetchEvent({
      accessToken: 'token',
      url: 'https://blaze.example.com/fhir/Patient'
    });

    const { fetch } = await callHandleFetch(
      event,
      new Response('ok'),
      'https://blaze.example.com/fhir/Patient?url=https://blaze.example.com/fhir'
    );

    const request = fetch.mock.calls[0][0] as Request;
    expect(request.url).toBe('http://backend:8080/fhir/Patient?url=https://blaze.example.com/fhir');
  });

  // A request to another origin isn't a backend request, so it must neither
  // be rewritten nor carry the access token.
  it.each([
    'https://other.host/x?redirect=https://blaze.example.com/fhir',
    'https://blaze.example.com.evil.org/fhir/Patient'
  ])('leaves the request to %s as it is', async (requestUrl) => {
    const event = fetchEvent({
      accessToken: 'token',
      url: 'https://blaze.example.com/fhir/Patient'
    });

    const { fetch } = await callHandleFetch(event, new Response('ok'), requestUrl);

    const request = fetch.mock.calls[0][0] as Request;
    expect(request.url).toBe(requestUrl);
    expect(request.headers.get('Authorization')).toBeNull();
    expect(request.headers.get('Forwarded')).toBeNull();
  });

  // The backend builds its links from the `Forwarded` header, so they have to
  // point at the origin the browser uses, which is the one of the page request.
  it('forwards the host and protocol of the page request', async () => {
    const event = fetchEvent({
      accessToken: 'token',
      url: 'https://blaze.example.com/fhir/Patient'
    });

    const { fetch } = await callHandleFetch(
      event,
      new Response('ok'),
      'https://blaze.example.com/fhir/Patient'
    );

    const request = fetch.mock.calls[0][0] as Request;
    expect(request.headers.get('Forwarded')).toBe('host=blaze.example.com;proto=https');
  });

  // The status is repeated inside the body because a request made from a
  // streamed load reaches the `{:catch}` block with the body alone.
  it('fails with 401 when there is no access token', async () => {
    const event = fetchEvent({});

    await expect(callHandleFetch(event, new Response('ok'))).rejects.toMatchObject({
      status: 401,
      body: { status: 401, message: 'Unauthorized.' }
    });
  });

  it('passes a successful response through', async () => {
    const event = fetchEvent({ accessToken: 'token' });

    const { result } = await callHandleFetch(event, new Response('ok'));

    expect(await result.text()).toBe('ok');
  });

  it('passes error responses through', async () => {
    const event = fetchEvent({ accessToken: 'token' });

    const { result } = await callHandleFetch(event, new Response('', { status: 404 }));

    expect(result.status).toBe(404);
  });

  // The backend rejecting the access token even though `handleAuthorization`
  // found the Better Auth session valid — revoked at the identity provider, clock
  // skew, a realm mismatch. Recovering here rather than in the load that made
  // the request is what keeps the return-to target: `event.url` is the page
  // the user is on, which the load cannot read.
  it('redirects to the sign-in page when the backend rejects the access token', async () => {
    const event = fetchEvent({ accessToken: 'token' });

    try {
      await callHandleFetch(event, new Response('', { status: 401 }));
      expect.fail('expected handleFetch to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
      expect((e as { location: string }).location).toBe(
        '/fhir/__sign-in?redirect=%2Ffhir%2FPatient'
      );
    }
  });

  // The redirect has to point at the page, not at the backend request that
  // failed. Those differ whenever a load fetches something other than the
  // resource the page shows.
  it('returns to the page, not to the backend URL that was rejected', async () => {
    const event = fetchEvent({
      accessToken: 'token',
      url: 'http://localhost/fhir/Patient?_count=50&_summary=true'
    });

    try {
      await callHandleFetch(
        event,
        new Response('', { status: 401 }),
        'http://localhost/fhir/metadata'
      );
      expect.fail('expected handleFetch to throw');
    } catch (e) {
      const location = (e as { location: string }).location;

      expect(new URL(location, 'http://localhost').searchParams.get('redirect')).toBe(
        '/fhir/Patient?_count=50&_summary=true'
      );
    }
  });

  it('passes a 403 through rather than redirecting', async () => {
    const event = fetchEvent({ accessToken: 'token' });

    const { result } = await callHandleFetch(event, new Response('', { status: 403 }));

    expect(result.status).toBe(403);
  });
});
