import type { PageServerLoad } from './$types';
import type { FhirResource } from 'fhir/r4';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(backendUrl(`/${params.type}/${params.id}/_history/${params.vid}`), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(
      res.status,
      res.status == 404
        ? `The ${params.type} with ID ${params.id} and version ${params.vid} was not found.`
        : `An error happened while loading the ${params.type} with ID ${params.id} and version ${params.vid}. Please try again later.`,
      { short: res.status == 404 ? 'Not Found' : undefined }
    );
  }

  return {
    resource: (await res.json()) as FhirResource
  };
};
