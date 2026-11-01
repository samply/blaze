import type { Bundle, CapabilityStatement, FhirResource, OperationOutcome } from 'fhir/r4';
import type { RouteParams } from './$types.js';
import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import { processParams } from '#lib/util.js';
import { searchMetadata, type SearchMetadata } from '#lib/search-metadata.js';

async function outcome(res: Response): Promise<OperationOutcome> {
  return (await res.json()) as OperationOutcome;
}

/**
 * Builds the error body for an unsuccessful search response.
 *
 * The status is left out because `error` adds it.
 */
export async function appError(
  params: RouteParams,
  res: Response
): Promise<Omit<App.Error, 'status'>> {
  switch (res.status) {
    case 400:
      return {
        short: 'Bad Request',
        message: (await outcome(res)).issue[0].diagnostics ?? 'Please check your search params.'
      };
    case 422:
      return {
        short: 'Unprocessable Content',
        message: (await outcome(res)).issue[0].diagnostics ?? 'Please check your search params.'
      };
    case 404:
      return {
        short: 'Not Found',
        message: `The resource type ${params.type} was not found.`
      };
    default:
      return {
        short: undefined,
        message: `An error happened while loading the ${params.type}s. Please try again later.`
      };
  }
}

/**
 * Fetches the search metadata of the resource type `type`.
 *
 * The search params, includes and reverse includes all come from a single
 * request, because they are all derived from the same CapabilityStatement.
 */
export async function fetchSearchMetadata(
  fetch: typeof window.fetch,
  type: string
): Promise<SearchMetadata> {
  const res = await fetch(backendUrl('/metadata'), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status, 'error while fetching the search metadata');
  }

  return searchMetadata((await res.json()) as CapabilityStatement, type);
}

export async function fetchBundleWithDuration(
  fetch: typeof window.fetch,
  params: RouteParams,
  url: URL
) {
  const start = Date.now();

  const res = await fetch(backendUrl(`/${params.type}`, processParams(url.searchParams)), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    const { message, ...properties } = await appError(params, res);
    error(res.status, message, properties);
  }

  return {
    bundle: (await res.json()) as Bundle<FhirResource>,
    duration: Date.now() - start
  };
}

export async function fetchPageBundleWithDuration(
  fetch: typeof window.fetch,
  params: RouteParams,
  pageId: string
) {
  const start = Date.now();

  const res = await fetch(backendUrl(`/${params.type}/__page/${pageId}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    const { message, ...properties } = await appError(params, res);
    error(res.status, message, properties);
  }

  return {
    bundle: (await res.json()) as Bundle<FhirResource>,
    duration: Date.now() - start
  };
}
