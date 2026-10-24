import { type Handle, type HandleFetch, redirect } from '@sveltejs/kit';
import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { sequence } from '@sveltejs/kit/hooks';
import { auth, handle as handleAuthentication } from '$lib/server/auth';
import { authenticate } from '$lib/server/session';
import { resolve as resolveRoute } from '$app/paths';

const signInPath = resolveRoute('/__sign-in');

/**
 * Builds the sign-in URL, remembering `target` as the page to return to
 * after signing in.
 *
 * Only the path and the query are kept, percent-encoded: unencoded, the
 * target's own `&` would end the `redirect` param and truncate the query,
 * and its origin has no business in a callback URL handed to Better Auth.
 */
function signInUrl(target: URL): string {
  return `${signInPath}?redirect=${encodeURIComponent(target.pathname + target.search)}`;
}

export const handleAuthorization: Handle = async ({ event, resolve }) => {
  if (event.route.id != '/__sign-in') {
    const session = await authenticate(auth.api, event.request.headers);

    if (!session) {
      redirect(307, signInUrl(event.url));
    }

    event.locals.session = session;
  }

  return resolve(event);
};

const authJsCookie = /^(__Secure-|__Host-)?authjs\./;

/**
 * Expires the cookies Auth.js set before the switch to Better Auth.
 *
 * Its session cookie alone is 3-4 KB, so together with the Better Auth
 * cookies, the Cookie header would exceed the 8 KB a reverse proxy like nginx
 * accepts by default.
 *
 * The cookies are expired on every page response and redirect, so they are
 * gone by the time the sign-in page is shown, before signing in adds the
 * Better Auth cookies. Responses of the Better Auth endpoints don't expire
 * them, because those bypass `resolve`, but they are only reached from the
 * sign-in page.
 */
export const expireAuthJsCookies: Handle = async ({ event, resolve }) => {
  for (const { name } of event.cookies.getAll()) {
    if (authJsCookie.test(name)) {
      event.cookies.delete(name, { path: '/' });
    }
  }

  return resolve(event);
};

export const handle = sequence(expireAuthJsCookies, handleAuthentication, handleAuthorization);

const origin = env.ORIGIN || '';
const forwarded = `host=${new URL(origin).host};proto=${new URL(origin).protocol.replace(':', '')}`;

function translateUrl(request: Request) {
  return request.url.replace(origin, env.BACKEND_BASE_URL || '');
}

export const handleFetch: HandleFetch = async ({ request, fetch, event }) => {
  const session = event.locals.session;

  if (!session?.accessToken) {
    error(401, {
      status: 401,
      short: undefined,
      message: `Unauthorized.`
    });
  }

  const headers: HeadersInit = {
    Accept: request.headers.get('accept') || 'application/fhir+json',
    Authorization: 'Bearer ' + session?.accessToken,
    Forwarded: forwarded
  };

  const contentType = request.headers.get('content-type');

  if (contentType) {
    headers['Content-Type'] = contentType;
  }

  request = new Request(translateUrl(request), {
    body: request.body,
    credentials: 'omit',
    headers: headers,
    method: request.method,
    // @ts-expect-error because duplex is not part of RequestInit
    duplex: 'half'
  });

  const res = await fetch(request);

  // The backend rejected a token that `handleAuthorization` considered valid:
  // revoked at the identity provider, clock skew, a realm mismatch. Recovering
  // here rather than in the load that made the request is what keeps the
  // return-to target — `event.url` is the page the user is on, which the load
  // cannot read.
  //
  // Both streamed loads await a backend request before they start streaming,
  // which is what lets this redirect still reach the browser; see
  // `docs/implementation/frontend.md`.
  if (res.status === 401) {
    redirect(307, signInUrl(event.url));
  }

  return res;
};
