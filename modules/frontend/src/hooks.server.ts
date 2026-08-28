import { type Handle, type HandleFetch, redirect } from '@sveltejs/kit';
import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { sequence } from '@sveltejs/kit/hooks';
import { handle as handleAuthentication } from '$lib/server/auth';
import { resolve as resolveRoute } from '$app/paths';

const signInPath = resolveRoute('/__sign-in');

/**
 * Builds the sign-in URL, remembering `target` as the page to return to
 * after signing in.
 *
 * Only the path and the query are kept, percent-encoded: unencoded, the
 * target's own `&` would end the `redirect` param and truncate the query,
 * and its origin has no business in a callback URL handed to Auth.js.
 */
function signInUrl(target: URL): string {
  return `${signInPath}?redirect=${encodeURIComponent(target.pathname + target.search)}`;
}

export const handleAuthorization: Handle = async ({ event, resolve }) => {
  if (event.route.id != '/__sign-in') {
    // authenticate the user
    const session = await event.locals.auth();

    if (!session || !session.expiresAt || session.expiresAt - Date.now() < 10000) {
      redirect(307, signInUrl(event.url));
    }

    event.locals.session = session;
  }

  return resolve(event);
};

export const handle = sequence(handleAuthentication, handleAuthorization);

function isApiRoute(url: string) {
  return url.endsWith('__search-params');
}

const origin = env.ORIGIN || '';
const forwarded = `host=${new URL(origin).host};proto=${new URL(origin).protocol.replace(':', '')}`;

function translateUrl(request: Request) {
  return request.url.replace(origin, env.BACKEND_BASE_URL || '');
}

export const handleFetch: HandleFetch = async ({ request, fetch, event }) => {
  if (isApiRoute(request.url)) {
    return fetch(request);
  }

  const session = event.locals.session;

  if (!session?.accessToken) {
    console.log('no session -> 401');
    error(401, {
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

  return fetch(request);
};
