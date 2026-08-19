import { error, type NumericRange } from '@sveltejs/kit';
import { ensureSignedIn, redirectIfSessionExpired } from '$lib/sign-in-navigation.js';

export type ErrorBody = App.Error | string;
export type ErrorBodySource = ErrorBody | ((res: Response) => ErrorBody | Promise<ErrorBody>);

async function resolveErrorBody(res: Response, source: ErrorBodySource): Promise<ErrorBody> {
  return typeof source === 'function' ? await source(res) : source;
}

function isValidErrorStatus(status: number): status is NumericRange<400, 599> {
  return status >= 400 && status <= 599;
}

function abort(res: Response, errorBody: ErrorBody): never {
  // `res.status` is not guaranteed to be a valid HTTP error status here, so
  // guard against it as defense in depth: SvelteKit's `error()` would throw
  // on an out-of-range status itself, producing an unrelated 500 with no
  // useful body — fall back to 500 explicitly instead. Currently, no known
  // code path delivers such a status: `handleAuthorization`'s
  // `isSubRequest` branch (`hooks.server.ts`) turns a server-side
  // sub-request's session expiry into a 401 rather than a 3xx redirect, and
  // the browser's `fetch` follows redirects by default.
  error(isValidErrorStatus(res.status) ? res.status : 500, errorBody);
}

/**
 * The common shape of an instance-level load's error body: 404 and
 * (optionally) 410 are reported specifically, everything else falls back
 * to a generic message.
 */
export function notFoundError(
  res: Pick<Response, 'status'>,
  messages: { notFound: string; gone?: string; default: string }
): App.Error {
  if (res.status === 404) {
    return { short: 'Not Found', message: messages.notFound };
  }

  if (messages.gone !== undefined && res.status === 410) {
    return { short: 'Gone', message: messages.gone };
  }

  return { short: undefined, message: messages.default };
}

/**
 * `notFoundError`, specialized for the common "a single FHIR resource,
 * identified by type and ID" case, where a 410 means the resource was
 * deleted and can still be found in its history.
 */
export function resourceNotFoundError(
  res: Pick<Response, 'status'>,
  type: string,
  id: string
): App.Error {
  return notFoundError(res, {
    notFound: `The ${type} with ID ${id} was not found.`,
    gone: `The ${type} with ID ${id} was deleted. Please look into the history.`,
    default: `An error happened while loading the ${type} with ID ${id}. Please try again later.`
  });
}

const defaultHeaders: HeadersInit = { Accept: 'application/fhir+json' };

/**
 * The common tail of `fetchJson` and `fetchJsonStreamed`, once each has
 * dealt with the session-expired case in its own way: abort the load with
 * a SvelteKit `error` on a non-ok response, otherwise parse the JSON body.
 */
async function parseOrAbort<T>(res: Response, errorBody: ErrorBodySource): Promise<T> {
  if (!res.ok) {
    abort(res, await resolveErrorBody(res, errorBody));
  }

  return (await res.json()) as T;
}

/**
 * Fetches `url` and parses the JSON body, redirecting to the sign-in page
 * if the session has expired and aborting the load with a SvelteKit
 * `error` on any other non-ok response.
 *
 * For use in universal load functions where the result is awaited
 * directly (not returned as a streamed promise) — a thrown `redirect`
 * relies on SvelteKit's own handling of the load's rejection to turn into
 * a navigation. See `fetchJsonStreamed` for the other case.
 */
export async function fetchJson<T>(
  fetch: typeof window.fetch,
  url: string | URL,
  errorBody: ErrorBodySource,
  headers: HeadersInit = defaultHeaders
): Promise<T> {
  const res = await fetch(url, { headers });

  ensureSignedIn(res);

  return parseOrAbort<T>(res, errorBody);
}

/**
 * Like `fetchJson`, but for streamed load data (returned from `load` as an
 * un-awaited promise and consumed via `{#await ...}{:catch}`) and other
 * contexts outside a load function, where a thrown `redirect` would not
 * cause a navigation — see `redirectIfSessionExpired`.
 */
export async function fetchJsonStreamed<T>(
  fetch: typeof window.fetch,
  url: string | URL,
  errorBody: ErrorBodySource,
  headers: HeadersInit = defaultHeaders
): Promise<T> {
  const res = await fetch(url, { headers });

  if (await redirectIfSessionExpired(res)) {
    return new Promise<never>(() => {});
  }

  return parseOrAbort<T>(res, errorBody);
}
