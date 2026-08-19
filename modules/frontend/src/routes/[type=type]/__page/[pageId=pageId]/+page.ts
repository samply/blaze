import type { PageLoad } from './$types';
import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';

import { fetchPageBundleWithDuration } from '../../util.js';
import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const { searchParams } = await fetchJson<{
    searchParams: CapabilityStatementRestResourceSearchParam[];
  }>(
    fetch,
    resolve('/[type=type]/__search-params', params),
    'error while fetching the search params',
    { Accept: 'application/json' }
  );

  return {
    searchParams,
    streamed: {
      start: Date.now(),
      bundle: fetchPageBundleWithDuration(fetch, params, params.pageId)
    }
  };
};
