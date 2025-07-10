import type { PageLoad } from './$types';
import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';

import { fetchPageBundleWithDuration } from '../../util.js';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

export const load: PageLoad = async ({ fetch, params }) => {
  const res = await fetch(`${base}/${params.type}/__search-params`, {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, 'error while fetching the search params');
  }

  return {
    searchParams: (await res.json()).searchParams as CapabilityStatementRestResourceSearchParam[],
    streamed: {
      start: Date.now(),
      bundle: fetchPageBundleWithDuration(fetch, params, params.pageId)
    }
  };
};
