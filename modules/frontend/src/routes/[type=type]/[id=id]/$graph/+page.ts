import type { PageLoad } from './$types';
import type { Bundle, GraphDefinition } from 'fhir/r5';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { fhirObject, transformBundle } from '$lib/resource/resource-card';

export const load: PageLoad = async ({ fetch, params, url }) => {
  const resourceRes = await fetch(resolve('/[type=type]/[id=id]', params), {
    headers: {
      Accept: 'application/fhir+json'
    }
  });

  if (!resourceRes.ok) {
    error(resourceRes.status as NumericRange<400, 599>, {
      short:
        resourceRes.status == 404 ? 'Not Found' : resourceRes.status == 410 ? 'Gone' : undefined,
      message:
        resourceRes.status == 404
          ? `The ${params.type} with ID ${params.id} was not found.`
          : resourceRes.status == 410
            ? `The ${params.type} with ID ${params.id} was deleted. Please look into the history.`
            : `An error happened while loading the ${params.type} with ID ${params.id}. Please try again later.`
    });
  }

  const resource = await resourceRes.json();

  const graphDefinitionsRes = await fetch(`${resolve('/GraphDefinition')}?_summary=true`, {
    headers: {
      Accept: 'application/fhir+json'
    }
  });

  if (!graphDefinitionsRes.ok) {
    error(graphDefinitionsRes.status as NumericRange<400, 599>, {
      short: undefined,
      message: `An error happened while loading GraphDefinitions. Please try again later.`
    });
  }

  const bundle: Bundle = await graphDefinitionsRes.json();

  let graphRes = undefined;
  if (url.searchParams.get('graph') !== null) {
    graphRes = await fetch(
      `${resolve('/[type=type]/[id=id]/$graph', params)}?graph=${url.searchParams.get('graph')}`,
      {
        headers: {
          Accept: 'application/fhir+json'
        }
      }
    );

    if (!graphRes.ok) {
      error(graphRes.status as NumericRange<400, 599>, {
        short: graphRes.status == 404 ? 'Not Found' : graphRes.status == 410 ? 'Gone' : undefined,
        message:
          graphRes.status == 404
            ? `The ${params.type} with ID ${params.id} was not found.`
            : graphRes.status == 410
              ? `The ${params.type} with ID ${params.id} was deleted. Please look into the history.`
              : `An error happened while loading the ${params.type} with ID ${params.id}. Please try again later.`
      });
    }
  }

  return {
    resource: await fhirObject(resource, fetch),
    graphDefinitions: bundle.entry?.map((e) => e.resource as GraphDefinition),
    selectedGraphDefinitionUrl: url.searchParams.get('graph'),
    graph: graphRes !== undefined ? await transformBundle(fetch, await graphRes.json()) : undefined
  };
};
