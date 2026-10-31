import { redirect, error } from '@sveltejs/kit';
import { ENABLE_AUTH } from '$app/env/public';
import {
  AUTH_CLIENT_ID,
  AUTH_CLIENT_SECRET,
  AUTH_ISSUER,
  AUTH_SECRET,
  BACKEND_BASE_URL,
  ORIGIN
} from '$app/env/private';
import { sequence, type Handle, type HandleFetch, type ServerInit } from '@sveltejs/kit/hooks';
import { authConfig } from '#lib/server/auth-config.js';
import { authHandle, createAuth } from '#lib/server/auth.js';
import { authenticate, type AuthApi } from '#lib/server/session.js';
import { createOriginCheck, warnAboutOrigin } from '#lib/server/origin.js';
import { resolve as resolveRoute } from '$app/paths';

/**
 * Better Auth, if auth is enabled.
 *
 * Without auth, the frontend can be deployed without login, for example as a
 * demo server. The admin section is removed then, because everybody could
 * use it.
 */
const auth = ENABLE_AUTH
  ? createAuth(authConfig({ AUTH_CLIENT_ID, AUTH_CLIENT_SECRET, AUTH_ISSUER, AUTH_SECRET }))
  : undefined;

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

/**
 * Lets only signed-in users through, redirecting all others to the sign-in
 * page.
 */
export function authorize(api: AuthApi): Handle {
  return async ({ event, resolve }) => {
    if (event.route.id != '/__sign-in') {
      const session = await authenticate(api, event.request.headers);

      if (!session) {
        redirect(307, signInUrl(event.url));
      }

      event.locals.session = session;
    }

    return resolve(event);
  };
}

/**
 * Returns true if the route with `routeId` is only available with auth: the
 * sign-in page and the admin section.
 */
function isAuthOnlyRoute(routeId: string | null): boolean {
  return routeId === '/__sign-in' || routeId === '/__admin' || !!routeId?.startsWith('/__admin/');
}

/**
 * Answers the routes only available with auth with 404.
 *
 * The check is done here rather than in the admin layout, because a form
 * action runs before any load.
 */
export const rejectAuthOnlyRoutes: Handle = ({ event, resolve }) => {
  if (isAuthOnlyRoute(event.route.id)) {
    error(404, 'Not Found');
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

export const handle = auth
  ? sequence(checkOrigin, expireAuthJsCookies, authHandle(auth), authorize(auth.api))
  : sequence(checkOrigin, expireAuthJsCookies, rejectAuthOnlyRoutes);

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
 * Swaps the origin of `url`, the one of the page request, for
 * `BACKEND_BASE_URL`.
 */
function translateUrl(url: URL) {
  return `${BACKEND_BASE_URL}${url.pathname}${url.search}`;
}

export const handleFetch: HandleFetch = async ({ request, fetch, event }) => {
  const url = new URL(request.url);

  // `event.fetch` resolves the root-relative backend URLs against the URL of
  // the page request, so only requests to its origin are backend requests.
  // Others go out as they are, without the access token.
  if (url.origin !== event.url.origin) {
    return fetch(request);
  }

  const session = event.locals.session;

  if (auth && !session?.accessToken) {
    error(401, `Unauthorized.`);
  }

  const headers: Record<string, string> = {
    Accept: request.headers.get('accept') || 'application/fhir+json',
    Forwarded: forwarded(event.url)
  };

  if (session) {
    headers['Authorization'] = 'Bearer ' + session.accessToken;
  }

  const contentType = request.headers.get('content-type');

  if (contentType) {
    headers['Content-Type'] = contentType;
  }

  request = new Request(translateUrl(url), {
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
  //
  // Without auth, there is no sign-in page to redirect to, so the 401 is
  // passed through.
  if (auth && res.status === 401) {
    redirect(307, signInUrl(event.url));
  }

  return res;
};
