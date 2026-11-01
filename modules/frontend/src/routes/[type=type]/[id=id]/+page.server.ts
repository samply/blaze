import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import type { FhirResource } from 'fhir/r4';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/${params.type}/${params.id}`), {
    headers: {
      Accept: 'application/fhir+json'
    }
  });

  if (!res.ok) {
    error(
      res.status,
      res.status == 404
        ? `The ${params.type} with ID ${params.id} was not found.`
        : res.status == 410
          ? `The ${params.type} with ID ${params.id} was deleted. Please look into the history.`
          : `An error happened while loading the ${params.type} with ID ${params.id}. Please try again later.`,
      { short: res.status == 404 ? 'Not Found' : res.status == 410 ? 'Gone' : undefined }
    );
  }

  return {
    resource: (await res.json()) as FhirResource
  };
};
