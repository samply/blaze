import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { loadSummary } from '$lib/summary.js';
import { fetchHistoryBundle } from '$lib/history.js';

export const load: PageLoad = async ({ fetch, url, parent }) => {
  const summary = await loadSummary(parent);
  const bundle = await fetchHistoryBundle(fetch, resolve('/_history'), url.searchParams, summary, {
    message: 'An error happened while loading the history. Please try again later.'
  });

  return { summary, bundle };
};
