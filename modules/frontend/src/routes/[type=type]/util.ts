import type { Bundle, CapabilityStatement, OperationOutcome } from 'fhir/r4';
import type { RouteParams } from './$types.js';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir, loadError } from '$lib/fetch.js';
import { processParams } from '$lib/util.js';
import { transformBundle } from '$lib/resource/resource-card.js';
import { searchMetadata, type SearchMetadata } from '$lib/search-metadata.js';

/**
 * The reason the server gave for rejecting a search.
 */
async function diagnostics(res: Response): Promise<string> {
  const outcome = (await res.json()) as OperationOutcome;

  return outcome.issue[0].diagnostics ?? 'Please check your search params.';
}

/**
 * The error body of a search on the resource type in `params`.
 */
export function searchError(params: RouteParams) {
  return loadError({
    400: diagnostics,
    422: diagnostics,
    404: `The resource type ${params.type} was not found.`,
    default: `An error happened while loading the ${params.type}s. Please try again later.`
  });
}

/**
 * Fetches the search metadata of the resource type `type`.
 *
 * The search params, includes and reverse includes all come from a single
 * request, because they are all derived from the same CapabilityStatement.
 *
 * @param fetch the `fetch` of the load
 * @param type the resource type to read the search metadata of
 * @returns the search metadata of `type`
 * @throws an `HttpError` with the status of the response if the metadata cannot
 * be read
 */
export async function fetchSearchMetadata(
  fetch: typeof window.fetch,
  type: string
): Promise<SearchMetadata> {
  const capabilityStatement = await fetchFhir<CapabilityStatement>(fetch, backendUrl('/metadata'), {
    error: 'error while fetching the search metadata'
  });

  return searchMetadata(capabilityStatement, type);
}

/**
 * Runs the search in `url` on the resource type in `params`.
 *
 * @param fetch the `fetch` of the load
 * @param params the route params, holding the resource type searched
 * @param url the URL of the page, whose search params are the search
 * @returns the transformed bundle and the time the search took
 * @throws an `HttpError` with the status of the response and the body built by
 * `searchError` if the response is not ok
 */
export async function fetchBundleWithDuration(
  fetch: typeof window.fetch,
  params: RouteParams,
  url: URL
) {
  const start = Date.now();

  const bundle = await fetchFhir<Bundle>(
    fetch,
    backendUrl(`/${params.type}`, processParams(url.searchParams)),
    { error: searchError(params) }
  );

  return {
    bundle: await transformBundle(fetch, bundle),
    duration: Date.now() - start
  };
}

/**
 * Reads the search page `pageId` of the resource type in `params`.
 *
 * @param fetch the `fetch` of the load
 * @param params the route params, holding the resource type searched
 * @param pageId the ID of the page to read
 * @returns the transformed bundle and the time the page took
 * @throws HttpError with the status of the response and the body built by
 * `searchError` if the response is not ok
 */
export async function fetchPageBundleWithDuration(
  fetch: typeof window.fetch,
  params: RouteParams,
  pageId: string
) {
  const start = Date.now();

  const bundle = await fetchFhir<Bundle>(fetch, backendUrl(`/${params.type}/__page/${pageId}`), {
    error: searchError(params)
  });

  return {
    bundle: await transformBundle(fetch, bundle),
    duration: Date.now() - start
  };
}
