import type { PageLoad } from './$types';
import type { Bundle, FhirResource, GraphDefinition } from 'fhir/r4';

import { resolve } from '$app/paths';
import { fhirObject, transformBundle } from '$lib/resource/resource-card';
import { fetchJson, resourceNotFoundError } from '$lib/fetch.js';

export const load: PageLoad = async ({ fetch, params, url }) => {
  const resource = await fetchJson<FhirResource>(
    fetch,
    resolve('/[type=type]/[id=id]', params),
    (res) => resourceNotFoundError(res, params.type, params.id)
  );

  const graphDefinitionsBundle = await fetchJson<Bundle>(
    fetch,
    `${resolve('/GraphDefinition')}?_summary=true`,
    'An error happened while loading GraphDefinitions. Please try again later.'
  );

  const graphParam = url.searchParams.get('graph');

  const graphBundle =
    graphParam !== null
      ? await fetchJson<Bundle>(
          fetch,
          `${resolve('/[type=type]/[id=id]/$graph', params)}?graph=${graphParam}`,
          (res) => resourceNotFoundError(res, params.type, params.id)
        )
      : undefined;

  return {
    resource: await fhirObject(resource, fetch),
    graphDefinitions: graphDefinitionsBundle.entry?.map((e) => e.resource as GraphDefinition),
    selectedGraphDefinitionUrl: graphParam,
    graph: graphBundle !== undefined ? await transformBundle(fetch, graphBundle) : undefined
  };
};
