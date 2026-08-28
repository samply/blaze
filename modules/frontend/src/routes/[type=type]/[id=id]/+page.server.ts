import type { FhirResource } from 'fhir/r4';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir, resourceError } from '$lib/fetch.js';
import { fhirObject } from '$lib/resource/resource-card.js';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const resource = await fetchFhir<FhirResource>(
    fetch,
    backendUrl(`/${params.type}/${params.id}`),
    { error: resourceError(params.type, params.id) }
  );

  return {
    resource: await fhirObject(resource, fetch)
  };
};
