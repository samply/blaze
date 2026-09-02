import type { PageServerLoad } from './$types';

import { fetchBundleWithDuration, fetchSearchMetadata } from './util.js';
import { summaryFromUrl } from '$lib/summary.js';

export const load: PageServerLoad = async ({ fetch, params, url }) => {
  return {
    summaryState: summaryFromUrl(url.searchParams),
    searchMetadata: await fetchSearchMetadata(fetch, params.type),
    streamed: {
      start: Date.now(),
      bundle: fetchBundleWithDuration(fetch, params, url)
    }
  };
};
