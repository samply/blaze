import type { PageServerLoad } from './$types';
import type { Bundle, FhirResource, GraphDefinition } from 'fhir/r4';

import { backendUrl } from '$lib/backend.js';
import { fetchFhir, resourceError } from '$lib/fetch.js';
import { fhirObject, transformBundle } from '$lib/resource/resource-card';

export const load: PageServerLoad = async ({ fetch, params, url }) => {
  const resource = await fetchFhir<FhirResource>(
    fetch,
    backendUrl(`/${params.type}/${params.id}`),
    { error: resourceError(params.type, params.id) }
  );

  const bundle = await fetchFhir<Bundle>(fetch, backendUrl('/GraphDefinition', '_summary=true'), {
    error: 'An error happened while loading GraphDefinitions. Please try again later.'
  });

  const selectedGraphDefinitionUrl = url.searchParams.get('graph');

  const graph =
    selectedGraphDefinitionUrl !== null
      ? await fetchFhir<Bundle>(
          fetch,
          backendUrl(`/${params.type}/${params.id}/$graph`, `graph=${selectedGraphDefinitionUrl}`),
          { error: resourceError(params.type, params.id) }
        )
      : undefined;

  return {
    resource: await fhirObject(resource, fetch),
    graphDefinitions: bundle.entry?.map((e) => e.resource as GraphDefinition),
    selectedGraphDefinitionUrl,
    graph: graph !== undefined ? await transformBundle(fetch, graph) : undefined
  };
};
