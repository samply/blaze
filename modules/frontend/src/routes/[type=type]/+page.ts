import type { PageLoad } from './$types';
import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';

import { fetchBundleWithDuration } from './util.js';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

async function loadSearchParams(
  fetch: typeof window.fetch,
  type: string
): Promise<CapabilityStatementRestResourceSearchParam[]> {
  const res = await fetch(`${base}/${type}/__search-params`, {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, 'error while fetching the search params');
  }

  return (await res.json()).searchParams;
}

export const load: PageLoad = async ({ fetch, params, url }) => {
  return {
    searchParams: await loadSearchParams(fetch, params.type),
    streamed: {
      start: Date.now(),
      bundle: fetchBundleWithDuration(fetch, params, url)
    }
  };
};
