import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { fhirObject } from '$lib/resource/resource-card.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
  const res = await fetch(`${base}/${params.type}/${params.id}`, {
    headers: {
      Accept: 'application/fhir+json'
    }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : res.status == 410 ? 'Gone' : undefined,
      message:
        res.status == 404
          ? `The ${params.type} with ID ${params.id} was not found.`
          : res.status == 410
            ? `The ${params.type} with ID ${params.id} was deleted. Please look into the history.`
            : `An error happened while loading the ${params.type} with ID ${params.id}. Please try again later.`
    });
  }

  const resource = await res.json();

  return {
    resource: await fhirObject(resource, fetch)
  };
};
