import type { PageLoad } from './$types';
import type { FhirResource } from 'fhir/r4';

import { resolve } from '$app/paths';
import { fhirObject } from '$lib/resource/resource-card.js';
import { fetchJson, notFoundError } from '$lib/fetch.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const resource = await fetchJson<FhirResource>(
    fetch,
    resolve('/[type=type]/[id=id]/_history/[vid=vid]', params),
    (res) =>
      notFoundError(res, {
        notFound: `The ${params.type} with ID ${params.id} and version ${params.vid} was not found.`,
        default: `An error happened while loading the ${params.type} with ID ${params.id} and version ${params.vid}. Please try again later.`
      })
  );

  return {
    resource: await fhirObject(resource, fetch)
  };
};
