import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { loadSummary } from '$lib/summary.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageLoad = async ({ fetch, params, url, parent }) => {
  const summary = await loadSummary(parent, params.type);
  const bundle = await fetchHistoryBundle(
    fetch,
    resolve('/[type=type]/_history', params),
    url.searchParams,
    summary,
    { message: `error while loading the ${params.type} history bundle` }
  );

  return { summary, bundle };
};
