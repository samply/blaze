import { describe, it, expect, vi } from 'vitest';
import { isHttpError, isRedirect } from '@sveltejs/kit';

vi.mock('$env/dynamic/private', () => ({
  env: { ORIGIN: 'http://localhost', BACKEND_BASE_URL: 'http://backend:8080' }
}));
vi.mock('$lib/server/auth', () => ({
  handle: (input: { event: unknown; resolve: unknown }) =>
    (input.resolve as (e: unknown) => unknown)(input.event)
}));

const { canRedirectToSignIn, handleAuthorization, handleFetch } = await import('./hooks.server.js');

function request(options: { accept?: string; method?: string } = {}): Request {
  const headers: Record<string, string> = {};

  if (options.accept !== undefined) {
    headers.accept = options.accept;
  }

  return new Request('http://localhost/Patient', { headers, method: options.method });
}

describe('canRedirectToSignIn test', () => {
  it('is true for a request without an Accept header', () => {
    // Non-browser clients (curl, wget, health checks) may send no Accept
    // header at all. They are treated as navigations, so they get a
    // redirect to the sign-in page rather than a bare 401.
    expect(
      canRedirectToSignIn({ isDataRequest: false, isSubRequest: false, request: request() })
    ).toBe(true);
  });

  it('is true for a browser document request (Accept starts with text/html)', () => {
    expect(
      canRedirectToSignIn({
        isDataRequest: false,
        isSubRequest: false,
        request: request({
          accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
        })
      })
    ).toBe(true);
  });

  it('is true for an Accept: */* request (e.g. the wget health check)', () => {
    // Confirmed via a local wget --spider run: wget sends `Accept: */*` and
    // no Sec-Fetch-Dest header, which is why Sec-Fetch-Dest alone cannot
    // discriminate this case.
    expect(
      canRedirectToSignIn({
        isDataRequest: false,
        isSubRequest: false,
        request: request({ accept: '*/*' })
      })
    ).toBe(true);
  });

  it('is false for a plain GET fetch request with an explicit FHIR JSON Accept header', () => {
    // Every `fetch` call in this codebase sets an explicit, non-HTML Accept
    // header — see `$lib/fetch.ts`. A plain `fetch` cannot usefully follow
    // a redirect to an HTML sign-in page (see `ensureSignedIn` in
    // `$lib/sign-in-navigation.ts`), so it gets the 401 branch instead.
    expect(
      canRedirectToSignIn({
        isDataRequest: false,
        isSubRequest: false,
        request: request({ accept: 'application/fhir+json' })
      })
    ).toBe(false);
  });

  it("is true for a form action POST request (Accept: application/json, as set by SvelteKit's own enhance())", () => {
    // Unlike a plain `fetch`, a `use:enhance`d form submission *can*
    // usefully follow a redirect: SvelteKit recognizes this exact same
    // signal (POST + `Accept: application/json`) and converts a thrown
    // `redirect()` into a `{ type: 'redirect' }` ActionResult
    // (`action_json_redirect` in `@sveltejs/kit`), which `applyAction` then
    // turns into a client-side navigation automatically — no extra
    // handling needed in `$lib/tailwind/form.svelte`. A thrown `error()`
    // does not get the same envelope treatment there, so this must take
    // the redirect branch rather than the 401 branch.
    expect(
      canRedirectToSignIn({
        isDataRequest: false,
        isSubRequest: false,
        request: request({ accept: 'application/json', method: 'POST' })
      })
    ).toBe(true);
  });

  it('is false for a GET request with an Accept: application/json header', () => {
    // Only the POST + application/json combination identifies a form
    // action; a plain JSON GET is a `fetchJson`/`fetchJsonStreamed` call.
    expect(
      canRedirectToSignIn({
        isDataRequest: false,
        isSubRequest: false,
        request: request({ accept: 'application/json', method: 'GET' })
      })
    ).toBe(false);
  });

  it('is true for a SvelteKit __data.json request, regardless of headers', () => {
    expect(
      canRedirectToSignIn({
        isDataRequest: true,
        isSubRequest: false,
        request: request({ accept: 'application/json' })
      })
    ).toBe(true);
  });

  it('is false for a server-side sub-request, regardless of headers', () => {
    // `event.fetch` calls made from a load function during SSR re-enter
    // `respond` as a sub-request without following redirects, so they must
    // not be classified as a navigation, or an expired session would fail
    // with an invalid redirect status instead of a 401.
    expect(
      canRedirectToSignIn({ isDataRequest: false, isSubRequest: true, request: request() })
    ).toBe(false);
  });
});

function fakeEvent(options: {
  routeId?: string | null;
  session?: { expiresAt?: number } | null;
  isDataRequest?: boolean;
  isSubRequest?: boolean;
  accept?: string;
  method?: string;
}) {
  const locals: { session?: unknown } = {};
  return {
    route: { id: options.routeId ?? '/[type=type]' },
    url: new URL('http://localhost/Patient'),
    isDataRequest: options.isDataRequest ?? false,
    isSubRequest: options.isSubRequest ?? false,
    request: request({ accept: options.accept, method: options.method }),
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

async function callHandleAuthorization(event: ReturnType<typeof fakeEvent>) {
  const resolve = vi.fn().mockResolvedValue(new Response('ok'));
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const result = await handleAuthorization({ event: event as any, resolve } as any);
  return { result, resolve };
}

describe('handleAuthorization test', () => {
  it('bypasses authorization for the sign-in route itself', async () => {
    const event = fakeEvent({ routeId: '/__sign-in', session: null });

    const { result, resolve } = await callHandleAuthorization(event);

    expect(resolve).toHaveBeenCalledWith(event);
    expect(await result.text()).toBe('ok');
  });

  it('sets locals.session and resolves normally for a valid session', async () => {
    const event = fakeEvent({ session: { expiresAt: Date.now() + 60000 } });

    const { resolve } = await callHandleAuthorization(event);

    expect(event.locals.session).toStrictEqual({ expiresAt: expect.any(Number) });
    expect(resolve).toHaveBeenCalledWith(event);
  });

  it('redirects a document request to the sign-in page when there is no session', async () => {
    const event = fakeEvent({ session: null, accept: 'text/html' });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
      expect((e as { location: string }).location).toBe('/fhir/__sign-in?redirect=%2FPatient');
    }
  });

  it('redirects a document request to the sign-in page when the session expires soon', async () => {
    const event = fakeEvent({
      session: { expiresAt: Date.now() + 1000 },
      accept: 'text/html'
    });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
    }
  });

  it('aborts a plain GET fetch request with a 401 when there is no session', async () => {
    const event = fakeEvent({ session: null, accept: 'application/fhir+json' });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isHttpError(e, 401)).toBe(true);
    }
  });

  it('redirects a form action POST request to the sign-in page when there is no session', async () => {
    const event = fakeEvent({ session: null, accept: 'application/json', method: 'POST' });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
    }
  });

  it('redirects a request without an Accept header (e.g. a health check) instead of aborting with a 401', async () => {
    const event = fakeEvent({ session: null });

    try {
      await callHandleAuthorization(event);
      expect.fail('expected handleAuthorization to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
    }
  });
});

function fetchEvent(options: { accept?: string; isDataRequest?: boolean } = {}) {
  return {
    url: new URL('http://localhost/Patient?_count=50'),
    isDataRequest: options.isDataRequest ?? false,
    isSubRequest: false,
    request: request({ accept: options.accept }),
    locals: { session: { accessToken: 'access-token' } }
  };
}

async function callHandleFetch(event: ReturnType<typeof fetchEvent>, response: Response) {
  const fetch = vi.fn().mockResolvedValue(response);
  const result = await handleFetch({
    request: new Request('http://localhost/fhir/Patient'),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetch: fetch as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    event: event as any
  });
  return { result, fetch };
}

describe('handleFetch test', () => {
  it('passes a successful backend response through', async () => {
    const { result } = await callHandleFetch(fetchEvent(), new Response('{}', { status: 200 }));

    expect(result.status).toBe(200);
  });

  // The backend rejecting the access token the frontend holds — revoked at
  // the identity provider, clock skew, a realm mismatch — even though
  // `handleAuthorization` found the Auth.js session valid and let the
  // request through. Only this hook knows the page the user is actually on
  // (`event.url`); the load function that made the request cannot read it
  // during SSR, so handling it there loses the return-to target.
  it('redirects to the sign-in page when the backend rejects the access token', async () => {
    try {
      await callHandleFetch(
        fetchEvent({ accept: 'text/html' }),
        new Response(null, { status: 401 })
      );
      expect.fail('expected handleFetch to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
      expect((e as { location: string }).location).toBe(
        '/fhir/__sign-in?redirect=%2FPatient%3F_count%3D50'
      );
    }
  });

  it('redirects a data request to the sign-in page as well', async () => {
    try {
      await callHandleFetch(
        fetchEvent({ accept: 'application/json', isDataRequest: true }),
        new Response(null, { status: 401 })
      );
      expect.fail('expected handleFetch to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
    }
  });

  // A redirect would be followed silently by the browser and hand the load
  // HTML where it expects JSON, so the unauthorized response has to reach
  // the client as-is and be handled there — see `ensureSignedIn` in
  // `$lib/sign-in-navigation.ts`.
  it('passes an unauthorized backend response through for a plain fetch', async () => {
    const { result } = await callHandleFetch(
      fetchEvent({ accept: 'application/fhir+json' }),
      new Response(null, { status: 401 })
    );

    expect(result.status).toBe(401);
  });
});
