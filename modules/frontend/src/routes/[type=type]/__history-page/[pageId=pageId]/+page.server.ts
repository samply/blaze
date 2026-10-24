import type { PageServerLoad } from './$types';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import { transformBundle } from '#lib/resource/resource-card.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/${params.type}/__history-page/${params.pageId}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status, `error while loading the ${params.type} history bundle`);
  }

  return { bundle: await transformBundle(fetch, await res.json()) };
};
