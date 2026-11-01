import type { PageServerLoad } from './$types';
import type { Bundle, FhirResource } from 'fhir/r4';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/${params.type}/__history-page/${params.pageId}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status, `error while loading the ${params.type} history bundle`);
  }

  return { bundle: (await res.json()) as Bundle<FhirResource> };
};
