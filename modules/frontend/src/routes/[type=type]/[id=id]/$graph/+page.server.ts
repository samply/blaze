import type { PageServerLoad } from './$types';
import type { Bundle, FhirResource, GraphDefinition } from 'fhir/r4';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import { appError, graphDefinitionsQuery, graphUrl } from './util.js';

export const load: PageServerLoad = async ({ fetch, params, url }) => {
  const resourceRes = await fetch(backendUrl(`/${params.type}/${params.id}`), {
    headers: {
      Accept: 'application/fhir+json'
    }
  });

  if (!resourceRes.ok) {
    const { message, ...properties } = await appError(params, resourceRes);
    error(resourceRes.status, message, properties);
  }

  const resource = await resourceRes.json();

  const graphDefinitionsRes = await fetch(
    backendUrl('/GraphDefinition', graphDefinitionsQuery(params.type)),
    {
      headers: {
        Accept: 'application/fhir+json'
      }
    }
  );

  if (!graphDefinitionsRes.ok) {
    error(
      graphDefinitionsRes.status,
      `An error happened while loading GraphDefinitions. Please try again later.`
    );
  }

  const bundle: Bundle = await graphDefinitionsRes.json();

  const graph = url.searchParams.get('graph');
  let graphRes = undefined;
  if (graph !== null) {
    graphRes = await fetch(graphUrl(params, graph), {
      headers: {
        Accept: 'application/fhir+json'
      }
    });

    if (!graphRes.ok) {
      const { message, ...properties } = await appError(params, graphRes);
      error(graphRes.status, message, properties);
    }
  }

  return {
    resource: resource as FhirResource,
    graphDefinitions: bundle.entry?.map((e) => e.resource as GraphDefinition),
    selectedGraphDefinitionUrl: graph,
    graph: graphRes !== undefined ? ((await graphRes.json()) as Bundle<FhirResource>) : undefined
  };
};
