import type { FhirResource } from 'fhir/r4';
import { resolve } from '$app/paths';
import { fhirObject } from '$lib/resource/resource-card.js';
import { fetchJson, resourceNotFoundError } from '$lib/fetch.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
  const resource = await fetchJson<FhirResource>(
    fetch,
    resolve('/[type=type]/[id=id]', params),
    (res) => resourceNotFoundError(res, params.type, params.id)
  );

  return {
    resource: await fhirObject(resource, fetch)
  };
};
