import { error, type NumericRange } from '@sveltejs/kit';

/**
 * The `App.Error` a load aborts with when its request fails, or a way to get
 * one.
 *
 * Most loads know their message up front, so a plain string or an `App.Error`
 * is enough. A function is for the loads whose body depends on the response,
 * which is what `loadError` builds.
 */
export type ErrorSource =
  string | App.Error | ((res: Response) => App.Error | string | Promise<App.Error | string>);

/**
 * The message a load reports for one status.
 *
 * A function is for a message only in the response, like the
 * diagnostics of an `OperationOutcome`.
 */
export type StatusMessage = string | ((res: Response) => string | Promise<string>);

/**
 * The message a load reports per status, and the one it reports for every
 * status it does not name.
 */
export type StatusMessages = { [status: number]: StatusMessage } & { default: StatusMessage };

/**
 * The reason phrase shown above the message, for the statuses the loads name
 * today. A load naming another one adds its phrase here, or its error is shown
 * without a label.
 */
const statusLabels: Record<number, string> = {
  400: 'Bad Request',
  404: 'Not Found',
  410: 'Gone',
  422: 'Unprocessable Content'
};

function messageOf(message: StatusMessage, res: Response): string | Promise<string> {
  return typeof message === 'function' ? message(res) : message;
}

/**
 * The error body of a request whose failure means something more specific than
 * a generic failure on certain statuses.
 */
export function loadError(messages: StatusMessages): (res: Response) => Promise<App.Error> {
  return async (res) => {
    const message: StatusMessage | undefined = messages[res.status];

    return message === undefined
      ? { message: await messageOf(messages.default, res) }
      : { short: statusLabels[res.status], message: await messageOf(message, res) };
  };
}

/**
 * The error body of a request reading the single FHIR resource `type` with ID
 * `id`, where a 410 means the resource was deleted and is still available in
 * its history.
 */
export function resourceError(type: string, id: string): (res: Response) => Promise<App.Error> {
  return loadError({
    404: `The ${type} with ID ${id} was not found.`,
    410: `The ${type} with ID ${id} was deleted. Please look into the history.`,
    default: `An error happened while loading the ${type} with ID ${id}. Please try again later.`
  });
}

/**
 * What a load supplies beyond the URL.
 *
 * An object rather than a positional argument so that the call site says what
 * the value is for, and so that an option can be added without touching every
 * caller.
 */
export interface FetchOptions {
  /**
   * The body the request fails with.
   *
   * The status of the response is added to it, because a rejected streamed
   * promise reaches its `{:catch}` block as the jsonified body alone, without
   * the `HttpError` that holds the status. A body bringing its own status keeps
   * it.
   */
  error: ErrorSource;
}

async function appErrorOf(source: ErrorSource, res: Response): Promise<App.Error> {
  const body = typeof source === 'function' ? await source(res) : source;

  return typeof body === 'string' ? { message: body } : body;
}

async function fetchAs<T>(
  fetch: typeof window.fetch,
  url: string,
  mediaType: string,
  options: FetchOptions
): Promise<T> {
  const res = await fetch(url, { headers: { Accept: mediaType } });

  if (!res.ok) {
    // `error` rejects a status outside 400–599, but a non-ok response outside
    // that range cannot reach here: the backend does not answer these requests
    // with a 3xx and `fetch` follows redirects.
    const status = res.status as NumericRange<400, 599>;

    error(status, { status, ...(await appErrorOf(options.error, res)) });
  }

  return (await res.json()) as T;
}

/**
 * Reads the resource at `url` as FHIR JSON, returning its parsed body.
 *
 * @param fetch the `fetch` of the load, so that the request passes through
 * `handleFetch`, which authorizes it and points it at the backend
 * @param url the URL to read, as built by `backendUrl`
 * @param options the error body a failed request aborts the load with
 * @returns the parsed body of a successful response
 * @throws HttpError with the status of the response and the body built
 * from `options.error` if the response is not ok
 */
export async function fetchFhir<T>(
  fetch: typeof window.fetch,
  url: string,
  options: FetchOptions
): Promise<T> {
  return fetchAs<T>(fetch, url, 'application/fhir+json', options);
}

/**
 * Reads the document at `url` as plain JSON, returning its parsed body.
 *
 * @param fetch the `fetch` of the load, so that the request passes through
 * `handleFetch`, which authorizes it and points it at the backend
 * @param url the URL to read, as built by `backendUrl`
 * @param options the error body a failed request aborts the load with
 * @returns the parsed body of a successful response
 * @throws HttpError with the status of the response and the body built
 * from `options.error` if the response is not ok
 */
export async function fetchJson<T>(
  fetch: typeof window.fetch,
  url: string,
  options: FetchOptions
): Promise<T> {
  return fetchAs<T>(fetch, url, 'application/json', options);
}
