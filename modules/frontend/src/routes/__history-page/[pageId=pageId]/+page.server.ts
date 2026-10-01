import type { PageServerLoad } from './$types';
import type { Bundle } from 'fhir/r4';

import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import { transformBundle } from '$lib/resource/resource-card.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const bundle = await fetchFhir<Bundle>(fetch, backendUrl(`/__history-page/${params.pageId}`), {
    error: { message: 'An error happened while loading the history. Please try again later.' }
  });

  return { bundle: await transformBundle(fetch, bundle) };
};
