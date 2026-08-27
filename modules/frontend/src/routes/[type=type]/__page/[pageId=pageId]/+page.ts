import type { PageLoad } from './$types';

import { fetchPageBundleWithDuration, fetchSearchMetadata } from '../../util.js';

export const load: PageLoad = async ({ fetch, params }) => {
  return {
    searchMetadata: await fetchSearchMetadata(fetch, params.type),
    streamed: {
      start: Date.now(),
      bundle: fetchPageBundleWithDuration(fetch, params, params.pageId)
    }
  };
};
