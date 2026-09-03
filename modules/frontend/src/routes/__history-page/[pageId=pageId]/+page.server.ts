import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { error, type NumericRange } from '@sveltejs/kit';
import { transformBundle } from '$lib/resource/resource-card.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/__history-page/${params.pageId}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      message: 'An error happened while loading the history. Please try again later.'
    });
  }

  return { bundle: await transformBundle(fetch, await res.json()) };
};
