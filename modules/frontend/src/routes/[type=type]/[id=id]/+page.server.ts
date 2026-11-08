import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import type { CapabilityStatement, FhirResource } from 'fhir/r4';
import { instanceOperations } from './operations.js';

/**
 * Returns the names of the operations on instances of `type` which have a page
 * in the frontend or no operations if the CapabilityStatement can't be loaded,
 * so that the resource is shown anyway.
 */
async function fetchOperations(fetch: typeof window.fetch, type: string): Promise<string[]> {
  const res = await fetch(backendUrl('/metadata'), {
    headers: { Accept: 'application/fhir+json' }
  });
  return res.ok ? instanceOperations((await res.json()) as CapabilityStatement, type) : [];
}

export const load: PageServerLoad = async ({ fetch, params }) => {
  const [res, operations] = await Promise.all([
    fetch(backendUrl(`/${params.type}/${params.id}`), {
      headers: {
        Accept: 'application/fhir+json'
      }
    }),
    fetchOperations(fetch, params.type)
  ]);

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
    resource: (await res.json()) as FhirResource,
    operations
  };
};
