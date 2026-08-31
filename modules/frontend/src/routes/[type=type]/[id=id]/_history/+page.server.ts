import type { PageServerLoad } from './$types';

import { base } from '$app/paths';
import { historySummaryFromUrl } from '$lib/history.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageServerLoad = async ({ fetch, params, url }) => {
  const summaryState = historySummaryFromUrl(url.searchParams);
  const bundle = await fetchHistoryBundle(
    fetch,
    `${base}/${params.type}/${params.id}/_history`,
    url.searchParams,
    { message: `error while loading the ${params.type}/${params.id} history bundle` }
  );

  return { summaryState, bundle: bundle };
};
