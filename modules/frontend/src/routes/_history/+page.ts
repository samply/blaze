import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { historySummaryFromUrl } from '$lib/history.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageLoad = async ({ fetch, url }) => {
  const summaryState = historySummaryFromUrl(url.searchParams);
  const bundle = await fetchHistoryBundle(fetch, resolve('/_history'), url.searchParams, {
    message: 'An error happened while loading the history. Please try again later.'
  });

  return { summaryState, bundle };
};
