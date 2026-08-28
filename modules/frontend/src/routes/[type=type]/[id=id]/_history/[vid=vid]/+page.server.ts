import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { error, type NumericRange } from '@sveltejs/kit';
import { fhirObject } from '$lib/resource/resource-card.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/${params.type}/${params.id}/_history/${params.vid}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : undefined,
      message:
        res.status == 404
          ? `The ${params.type} with ID ${params.id} and version ${params.vid} was not found.`
          : `An error happened while loading the ${params.type} with ID ${params.id} and version ${params.vid}. Please try again later.`
    });
  }

  const resource = await res.json();

  return {
    resource: await fhirObject(resource, fetch)
  };
};
