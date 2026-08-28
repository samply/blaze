import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { historySummaryFromUrl } from '$lib/history.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageServerLoad = async ({ fetch, params, url }) => {
  const summaryState = historySummaryFromUrl(url.searchParams);
  const bundle = await fetchHistoryBundle(
    fetch,
    backendUrl(`/${params.type}/${params.id}/_history`),
    url.searchParams,
    { message: `error while loading the ${params.type}/${params.id} history bundle` }
  );

  return { summaryState, bundle: bundle };
};
