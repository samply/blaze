import type { Bundle, StructureDefinition } from 'fhir/r4';
import { resolve } from '$app/paths';
import { error } from '@sveltejs/kit';
import { fetchJson } from '$lib/fetch.js';

const structureDefinitionStore = new Map<string, Promise<StructureDefinition>>();

function structureDefinitionUrl(type: string) {
  return `${resolve('/[type=type]', { type: 'StructureDefinition' })}?url=http://hl7.org/fhir/StructureDefinition/${type}`;
}

async function loadStructureDefinition(fetch: typeof window.fetch, type: string) {
  const bundle = await fetchJson<Bundle>(
    fetch,
    structureDefinitionUrl(type),
    `error while loading the ${type} StructureDefinition`
  );

  if (bundle.entry === undefined) {
    error(404, `expected one bundle entry but found none`);
  }

  if (bundle.entry?.length != 1) {
    error(404, `expected one bundle entry but found ${bundle.entry?.length}`);
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

  const load = loadStructureDefinition(fetch, type);

  structureDefinitionStore.set(type, load);

  return load;
}
