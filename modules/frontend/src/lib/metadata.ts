import type { Bundle, StructureDefinition } from 'fhir/r4';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

const structureDefinitionStore = new Map<string, Promise<StructureDefinition>>();

function structureDefinitionUrl(type: string) {
  return `${base}/StructureDefinition?url=http://hl7.org/fhir/StructureDefinition/${type}`;
}

/**
 * Builds the error body of a failed StructureDefinition load.
 *
 * The status is carried in the body as well, because StructureDefinitions are
 * loaded while transforming a streamed bundle: a rejected streamed promise
 * reaches the `{:catch}` block as the jsonified body alone, without the
 * `HttpError` that holds the status.
 */
function appError(status: NumericRange<400, 599>, message: string): App.Error {
  return { status, short: undefined, message };
}

async function loadStructureDefinition(fetch: typeof window.fetch, type: string) {
  const res = await fetch(structureDefinitionUrl(type), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    const status = res.status as NumericRange<400, 599>;
    error(status, appError(status, `error while loading the ${type} StructureDefinition`));
  }

  const bundle = (await res.json()) as Bundle;

  if (bundle.entry === undefined) {
    error(404, appError(404, `expected one bundle entry but found none`));
  }

  if (bundle.entry?.length != 1) {
    error(404, appError(404, `expected one bundle entry but found ${bundle.entry?.length}`));
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
