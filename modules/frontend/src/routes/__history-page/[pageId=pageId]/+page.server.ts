import type { PageServerLoad } from './$types';
import type { Bundle, FhirResource } from 'fhir/r4';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/__history-page/${params.pageId}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status, 'An error happened while loading the history. Please try again later.');
  }

  return { bundle: (await res.json()) as Bundle<FhirResource> };
};
