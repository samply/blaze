import { redirect, error } from '@sveltejs/kit';
import { BACKEND_BASE_URL, ORIGIN } from '$app/env/private';
import { sequence, type Handle, type HandleFetch, type ServerInit } from '@sveltejs/kit/hooks';
import { auth, handle as handleAuthentication } from '#lib/server/auth.js';
import { authenticate } from '#lib/server/session.js';
import { createOriginCheck, warnAboutOrigin } from '#lib/server/origin.js';
import { resolve as resolveRoute } from '$app/paths';

const signInPath = resolveRoute('__sign-in');

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

export const init: ServerInit = () => {
  warnAboutOrigin(ORIGIN, (message) => console.warn(message));
};

const checkRequestOrigin = createOriginCheck(ORIGIN, (message) => console.error(message));

/**
 * Logs an error once if the origin derived from a request differs from the
 * no longer supported `ORIGIN`.
 */
export const checkOrigin: Handle = ({ event, resolve }) => {
  checkRequestOrigin(event.url.origin);
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

export const handle = sequence(
  checkOrigin,
  expireAuthJsCookies,
  handleAuthentication,
  handleAuthorization
);

/**
 * The `Forwarded` header telling the backend the origin the browser uses, so
 * that the links it builds point there.
 *
 * The origin is the one adapter-node derived for the page request from the
 * `Host` (or `HOST_HEADER`) and `PROTOCOL_HEADER` headers.
 */
function forwarded(url: URL) {
  return `host=${url.host};proto=${url.protocol.replace(':', '')}`;
}

/**
 * Swaps the origin of the page request for `BACKEND_BASE_URL`.
 *
 * `event.fetch` resolves the root-relative backend URLs against the URL of
 * the page request, so its origin is exactly the one to swap.
 */
function translateUrl(request: Request, url: URL) {
  return request.url.replace(url.origin, BACKEND_BASE_URL);
}

export const handleFetch: HandleFetch = async ({ request, fetch, event }) => {
  const session = event.locals.session;

  if (!session?.accessToken) {
    error(401, `Unauthorized.`);
  }

  const headers: HeadersInit = {
    Accept: request.headers.get('accept') || 'application/fhir+json',
    Authorization: 'Bearer ' + session?.accessToken,
    Forwarded: forwarded(event.url)
  };

  const contentType = request.headers.get('content-type');

  if (contentType) {
    headers['Content-Type'] = contentType;
  }

  request = new Request(translateUrl(request, event.url), {
    body: request.body,
    credentials: 'omit',
    headers,
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
