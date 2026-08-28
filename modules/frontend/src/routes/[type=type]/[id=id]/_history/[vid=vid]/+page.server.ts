import type { PageServerLoad } from './$types';
import type { FhirResource } from 'fhir/r4';

import { backendUrl } from '$lib/backend.js';
import { fetchFhir, loadError } from '$lib/fetch.js';
import { fhirObject } from '$lib/resource/resource-card.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const resource = await fetchFhir<FhirResource>(
    fetch,
    backendUrl(`/${params.type}/${params.id}/_history/${params.vid}`),
    {
      error: loadError({
        404: `The ${params.type} with ID ${params.id} and version ${params.vid} was not found.`,
        default: `An error happened while loading the ${params.type} with ID ${params.id} and version ${params.vid}. Please try again later.`
      })
    }
  );

  return {
    resource: await fhirObject(resource, fetch)
  };
};
