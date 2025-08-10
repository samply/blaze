import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { fhirObject } from '$lib/resource/resource-card.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const res = await fetch(resolve('/[type=type]/[id=id]/_history/[vid=vid]', params), {
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
