import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { historySummaryFromUrl } from '$lib/history.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageServerLoad = async ({ fetch, url }) => {
  const summaryState = historySummaryFromUrl(url.searchParams);
  const bundle = await fetchHistoryBundle(fetch, backendUrl('/_history'), url.searchParams, {
    message: 'An error happened while loading the history. Please try again later.'
  });

  return { summaryState, bundle };
};
