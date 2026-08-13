import { type Handle, type HandleFetch, type RequestEvent, redirect } from '@sveltejs/kit';
import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { sequence } from '@sveltejs/kit/hooks';
import { handle as handleAuthentication } from '$lib/server/auth';
import { sessionExpired, signInUrl } from '$lib/sign-in';

/**
 * Whether `request`'s Accept header indicates a browser-initiated document
 * load — one that prioritizes `text/html` — as opposed to a plain `fetch`
 * call, which every load function and component in this codebase makes
 * with an explicit, non-HTML Accept header (see `$lib/fetch.ts`), as does
 * SvelteKit's own `enhance()` for form actions (`Accept: application/json`).
 *
 * `Sec-Fetch-Dest` would be the more direct signal for this, but Safari's
 * WebKit does not send it reliably. SvelteKit's own equivalent decision —
 * HTML vs. JSON error rendering, see `handle_fatal_error` in
 * `@sveltejs/kit` — uses the Accept header for the same reason.
 *
 * A missing or wildcard (`Accept: * / *`, e.g. the Docker health check's
 * `wget --spider`, confirmed by inspecting its actual request) Accept
 * header is treated as a document load too, so that such unrecognized
 * clients get a redirect to the sign-in page rather than a bare 401.
 */
function acceptsHtmlDocument(request: Request): boolean {
  const accept = request.headers.get('accept');

  if (accept === null) {
    return true;
  }

  return accept.split(',').some((part) => {
    const mediaType = part.split(';')[0].trim();
    return mediaType === 'text/html' || mediaType === 'text/*' || mediaType === '*/*';
  });
}

/**
 * Whether `request` is a `use:enhance`d form action submission (see
 * `$lib/tailwind/form.svelte`), identified by the exact same signal
 * SvelteKit itself uses for this (`is_action_json_request` in
 * `@sveltejs/kit`): a POST with an `Accept: application/json` header, which
 * `enhance()` always sends.
 *
 * Unlike a plain `fetch`, such a request *can* usefully follow a redirect:
 * SvelteKit converts a thrown `redirect()` into a `{ type: 'redirect' }`
 * `ActionResult` for it (`action_json_redirect` in `@sveltejs/kit`), which
 * `applyAction` then turns into a client-side navigation automatically. A
 * thrown `error()` does not get the same envelope treatment when it
 * originates from a hook (as opposed to the action itself) — `deserialize()`
 * would see a plain `{ message }` body with no `type` field — so this must
 * take the redirect branch, not the 401 branch, in `handleAuthorization`.
 *
 * This assumes no `+server.ts` route in this app accepts a JSON POST outside
 * a form action; if one is ever added, it would be misclassified as a
 * redirectable request too.
 */
function isFormActionRequest(request: Request): boolean {
  return request.method === 'POST' && request.headers.get('accept') === 'application/json';
}

/**
 * Distinguishes requests that can be redirected to the sign-in page —
 * document loads, SvelteKit `__data.json` requests, and form action
 * submissions, all of which the browser or SvelteKit's client runtime turn
 * into a navigation — from plain `fetch` calls, which cannot usefully
 * follow a redirect to an HTML sign-in page (see `$lib/sign-in.ts`).
 *
 * A server-side sub-request (`event.fetch` called from a load function
 * during SSR) must never be classified as redirectable regardless of its
 * headers: it re-enters `resolve` without following redirects, so a 307
 * response would reach `fetchJson`/`fetchJsonStreamed` as-is and `error()`
 * would then reject it as an invalid HTTP status.
 */
export function canRedirectToSignIn(
  event: Pick<RequestEvent, 'isDataRequest' | 'isSubRequest' | 'request'>
) {
  if (event.isSubRequest) {
    return false;
  }

  return (
    event.isDataRequest || acceptsHtmlDocument(event.request) || isFormActionRequest(event.request)
  );
}

export const handleAuthorization: Handle = async ({ event, resolve }) => {
  if (event.route.id != '/__sign-in') {
    // authenticate the user
    const session = await event.locals.auth();

    if (!session || !session.expiresAt || session.expiresAt - Date.now() < 10000) {
      if (canRedirectToSignIn(event)) {
        redirect(307, signInUrl(event.url));
      }

      // RFC 6750 section 3: signal an expired/invalid access token with a
      // 401 instead of a redirect, which a plain `fetch` call cannot
      // usefully follow (see `$lib/sign-in-navigation.ts#ensureSignedIn`).
      error(401, 'The session has expired. Please sign in again.');
    }

    event.locals.session = session;
  }

  return resolve(event);
};

export const handle = sequence(handleAuthentication, handleAuthorization);

function isApiRoute(url: string) {
  return (
    url.endsWith('__search-params') ||
    url.endsWith('__search-includes') ||
    url.endsWith('__search-rev-includes')
  );
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

  const response = await fetch(request);

  // The backend rejected the access token even though `handleAuthorization`
  // found the Auth.js session valid — revoked at the identity provider,
  // clock skew, a realm mismatch. Recovering from that here rather than in
  // the load function that made the request is what keeps the return-to
  // target: `event.url` is the page the user is on, which a load cannot
  // read during SSR.
  if (sessionExpired(response) && canRedirectToSignIn(event)) {
    redirect(307, signInUrl(event.url));
  }

  return response;
};
