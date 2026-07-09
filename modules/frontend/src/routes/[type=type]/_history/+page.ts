import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { historySummaryFromUrl } from '$lib/history.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageLoad = async ({ fetch, params, url }) => {
  const summaryState = historySummaryFromUrl(url.searchParams);
  const bundle = await fetchHistoryBundle(
    fetch,
    resolve('/[type=type]/_history', params),
    url.searchParams,
    { message: `error while loading the ${params.type} history bundle` }
  );

  return { summaryState, bundle };
};
