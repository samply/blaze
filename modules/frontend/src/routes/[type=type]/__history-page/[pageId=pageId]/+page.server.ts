import type { PageServerLoad } from './$types';
import type { Bundle } from 'fhir/r4';

import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import { transformBundle } from '$lib/resource/resource-card.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const bundle = await fetchFhir<Bundle>(
    fetch,
    backendUrl(`/${params.type}/__history-page/${params.pageId}`),
    { error: `error while loading the ${params.type} history bundle` }
  );

  return { bundle: await transformBundle(fetch, bundle) };
};
