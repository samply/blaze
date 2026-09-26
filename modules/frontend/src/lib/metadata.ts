import type { Bundle, StructureDefinition } from 'fhir/r4';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import { error } from '@sveltejs/kit';

const structureDefinitionStore = new Map<string, Promise<StructureDefinition>>();

function structureDefinitionUrl(type: string) {
  return backendUrl('/StructureDefinition', `url=http://hl7.org/fhir/StructureDefinition/${type}`);
}

async function loadStructureDefinition(fetch: typeof window.fetch, type: string) {
  const bundle = await fetchFhir<Bundle>(fetch, structureDefinitionUrl(type), {
    error: `error while loading the ${type} StructureDefinition`
  });

  if (bundle.entry === undefined) {
    error(404, { status: 404, message: `expected one bundle entry but found none` });
  }

  if (bundle.entry?.length != 1) {
    error(404, {
      status: 404,
      message: `expected one bundle entry but found ${bundle.entry?.length}`
    });
  }

  return bundle.entry[0].resource as StructureDefinition;
}

export async function fetchStructureDefinition(
  type: string,
  fetch: typeof window.fetch = window.fetch
) {
  const cached = structureDefinitionStore.get(type);
  if (cached !== undefined) {
    return cached;
  }

  // Only successful loads stay in the cache. A failed one is removed before the
  // rejection is passed on, so that the next call retries it. The cached
  // promise is the one returned by `catch`, so concurrent callers all observe
  // the same rejection.
  const load = loadStructureDefinition(fetch, type).catch((e) => {
    structureDefinitionStore.delete(type);
    throw e;
  });

  structureDefinitionStore.set(type, load);

  return load;
}
