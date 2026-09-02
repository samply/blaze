import { describe, it, expect, vi } from 'vitest';
import { isRedirect } from '@sveltejs/kit';

vi.mock('$env/dynamic/private', () => ({
  env: { ORIGIN: 'http://localhost', BACKEND_BASE_URL: 'http://backend:8080' }
}));
vi.mock('$lib/server/auth', () => ({
  handle: (input: { event: unknown; resolve: unknown }) =>
    (input.resolve as (e: unknown) => unknown)(input.event)
}));

const { handleAuthorization, handleFetch } = await import('./hooks.server.js');

function mockEvent(options: {
  routeId?: string | null;
  session?: { expiresAt?: number } | null;
  url?: string;
}) {
  const locals: { session?: unknown } = {};
  return {
    route: { id: options.routeId ?? '/[type=type]' },
    url: new URL(options.url ?? 'http://localhost/Patient'),
    locals: {
      auth: async () => options.session,
      get session() {
        return locals.session;
      },
      set session(value) {
        locals.session = value;
      }
    }
  };
}

async function callHandleAuthorization(event: ReturnType<typeof mockEvent>) {
  const resolve = vi.fn().mockResolvedValue(new Response('ok'));
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const result = await handleAuthorization({ event: event as any, resolve } as any);
  return { result, resolve };
}

describe('handleAuthorization test', () => {
  it('bypasses authorization for the sign-in route itself', async () => {
    const event = mockEvent({ routeId: '/__sign-in', session: null });

    const { result, resolve } = await callHandleAuthorization(event);

    expect(resolve).toHaveBeenCalledWith(event);
    expect(await result.text()).toBe('ok');
  });

  it('sets locals.session and resolves normally for a valid session', async () => {
    const event = mockEvent({ session: { expiresAt: Date.now() + 60000 } });

    const { resolve } = await callHandleAuthorization(event);

    expect(event.locals.session).toStrictEqual({ expiresAt: expect.any(Number) });
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

  it('redirects to the sign-in page when the session expires soon', async () => {
    const event = mockEvent({ session: { expiresAt: Date.now() + 1000 } });

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
});
