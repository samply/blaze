import type { PageLoad } from './$types';
import type { Bundle } from 'fhir/r4';

import { resolve } from '$app/paths';
import { transformBundle } from '$lib/resource/resource-card.js';
import { fetchJson } from '$lib/fetch.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const bundle = await fetchJson<Bundle>(
    fetch,
    resolve('/__history-page/[pageId=pageId]', params),
    'An error happened while loading the history. Please try again later.'
  );

  return { bundle: await transformBundle(fetch, bundle) };
};
