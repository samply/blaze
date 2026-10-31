import { describe, it, expect, vi } from 'vitest';
import { isHttpError } from '@sveltejs/kit';

vi.mock('$app/env/public', () => ({ ENABLE_AUTH: false }));
vi.mock('$app/env/private', () => ({
  BACKEND_BASE_URL: 'http://backend:8080',
  AUTH_CLIENT_ID: '',
  AUTH_CLIENT_SECRET: '',
  AUTH_ISSUER: '',
  AUTH_SECRET: '',
  ORIGIN: ''
}));
const createAuth = vi.hoisted(() => vi.fn());
vi.mock('#lib/server/auth.js', () => ({ createAuth, authHandle: vi.fn() }));

const { handleFetch, rejectAuthOnlyRoutes } = await import('./hooks.server.js');
// captured right away, because the mocks are cleared before each test
const createAuthCalls = [...createAuth.mock.calls];

describe('auth test', () => {
  it("doesn't create Better Auth", () => {
    expect(createAuthCalls).toStrictEqual([]);
  });
});

async function callRejectAuthOnlyRoutes(routeId: string | null) {
  const event = { route: { id: routeId } };
  const resolve = vi.fn().mockResolvedValue(new Response('ok'));
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const result = await rejectAuthOnlyRoutes({ event: event as any, resolve } as any);
  return { event, result, resolve };
}

describe('rejectAuthOnlyRoutes test', () => {
  // Without auth, there is nobody to sign in and the admin section is
  // removed, because everybody could use it.
  it.each([
    '/__sign-in',
    '/__admin',
    '/__admin/jobs',
    '/__admin/jobs/new/re-index',
    '/__admin/dbs/[dbId=id]'
  ])('answers %s with 404', async (routeId) => {
    try {
      await callRejectAuthOnlyRoutes(routeId);
      expect.fail('expected rejectAuthOnlyRoutes to throw');
    } catch (e) {
      expect(isHttpError(e, 404)).toBe(true);
    }
  });

  it.each(['/', '/[type=type]', '/_history', '/metadata', null])('resolves %s', async (routeId) => {
    const { event, result, resolve } = await callRejectAuthOnlyRoutes(routeId);

    expect(resolve).toHaveBeenCalledWith(event);
    expect(await result.text()).toBe('ok');
  });
});

async function callHandleFetch(response: Response) {
  const event = { url: new URL('http://localhost/fhir/Patient'), locals: {} };
  const fetch = vi.fn().mockResolvedValue(response);
  const result = await handleFetch({
    request: new Request('http://localhost/fhir/Patient'),
    fetch,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    event: event as any
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any);
  return { result, fetch };
}

describe('handleFetch test', () => {
  it('sends the request to the backend without an access token', async () => {
    const { fetch } = await callHandleFetch(new Response('ok'));

    const request = fetch.mock.calls[0][0] as Request;
    expect(request.url).toBe('http://backend:8080/fhir/Patient');
    expect(request.headers.get('Authorization')).toBeNull();
    expect(request.headers.get('Forwarded')).toBe('host=localhost;proto=http');
  });

  // There is no sign-in page to redirect to.
  it('passes a 401 through', async () => {
    const { result } = await callHandleFetch(new Response('', { status: 401 }));

    expect(result.status).toBe(401);
  });
});
