import type { PageLoad } from './$types';
import type { Bundle } from 'fhir/r4';

import { resolve } from '$app/paths';
import { transformBundle } from '$lib/resource/resource-card.js';
import { fetchJson } from '$lib/fetch.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const bundle = await fetchJson<Bundle>(
    fetch,
    resolve('/[type=type]/__history-page/[pageId=pageId]', params),
    `error while loading the ${params.type} history bundle`
  );

  return { bundle: await transformBundle(fetch, bundle) };
};
