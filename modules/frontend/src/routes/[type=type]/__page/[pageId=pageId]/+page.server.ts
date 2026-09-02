import type { PageServerLoad } from './$types';

import { fetchPageBundleWithDuration, fetchSearchMetadata } from '../../util.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  return {
    searchMetadata: await fetchSearchMetadata(fetch, params.type),
    streamed: {
      start: Date.now(),
      bundle: fetchPageBundleWithDuration(fetch, params, params.pageId)
    }
  };
};
