import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { historySummaryFromUrl } from '$lib/history.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageServerLoad = async ({ fetch, params, url }) => {
  const summaryState = historySummaryFromUrl(url.searchParams);
  const bundle = await fetchHistoryBundle(
    fetch,
    backendUrl(`/${params.type}/_history`),
    url.searchParams,
    { message: `error while loading the ${params.type} history bundle` }
  );

  return { summaryState, bundle };
};
