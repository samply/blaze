import type { PageLoad } from './$types';
import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';

import { fetchBundleWithDuration } from './util.js';
import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';
import { summaryFromUrl } from '$lib/summary.js';

async function loadSearchParams(
  fetch: typeof window.fetch,
  type: string
): Promise<CapabilityStatementRestResourceSearchParam[]> {
  const { searchParams } = await fetchJson<{
    searchParams: CapabilityStatementRestResourceSearchParam[];
  }>(
    fetch,
    resolve('/[type=type]/__search-params', { type: type }),
    'error while fetching the search params',
    { Accept: 'application/json' }
  );

  return searchParams;
}

export const load: PageLoad = async ({ fetch, params, url }) => {
  return {
    summaryState: summaryFromUrl(url.searchParams),
    searchParams: await loadSearchParams(fetch, params.type),
    streamed: {
      start: Date.now(),
      bundle: fetchBundleWithDuration(fetch, params, url)
    }
  };
};
