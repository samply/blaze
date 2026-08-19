import type { Bundle, OperationOutcome } from 'fhir/r4';
import type { RouteParams } from './$types.js';
import { resolve } from '$app/paths';
import { processParams } from '$lib/util.js';
import { transformBundle } from '$lib/resource/resource-card.js';
import { fetchJsonStreamed } from '$lib/fetch.js';

async function outcome(res: Response): Promise<OperationOutcome> {
  return (await res.json()) as OperationOutcome;
}

export async function appError(params: RouteParams, res: Response) {
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

// `fetchBundleWithDuration` and `fetchPageBundleWithDuration` are returned
// from `load` as an un-awaited, streamed promise (see `+page.ts`) and
// consumed via `{#await ...}{:catch}`, so they use `fetchJsonStreamed`
// rather than `fetchJson` — see its doc comment for why.

export async function fetchBundleWithDuration(
  fetch: typeof window.fetch,
  params: RouteParams,
  url: URL
) {
  const start = Date.now();

  const bundle = await fetchJsonStreamed<Bundle>(
    fetch,
    `${resolve('/[type=type]', params)}?${processParams(url.searchParams)}`,
    (res) => appError(params, res)
  );

  return {
    bundle: await transformBundle(fetch, bundle),
    duration: Date.now() - start
  };
}

export async function fetchPageBundleWithDuration(
  fetch: typeof window.fetch,
  params: RouteParams,
  pageId: string
) {
  const start = Date.now();

  const bundle = await fetchJsonStreamed<Bundle>(
    fetch,
    resolve('/[type=type]/__page/[pageId=pageId]', { type: params.type, pageId: pageId }),
    (res) => appError(params, res)
  );

  return {
    bundle: await transformBundle(fetch, bundle),
    duration: Date.now() - start
  };
}
