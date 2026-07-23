import type { PageLoad } from './$types';
import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';

import { fetchPageBundleWithDuration } from '../../util.js';
import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { loadSummary } from '$lib/summary.js';

export const load: PageLoad = async ({ fetch, params, parent }) => {
  const summary = await loadSummary(parent, params.type);
  const res = await fetch(resolve('/[type=type]/__search-params', params), {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, 'error while fetching the search params');
  }

  return {
    summary,
    searchParams: (await res.json()).searchParams as CapabilityStatementRestResourceSearchParam[],
    streamed: {
      start: Date.now(),
      bundle: fetchPageBundleWithDuration(fetch, params, params.pageId)
    }
  };
};
