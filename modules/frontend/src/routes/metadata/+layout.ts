import { fhirObject } from '$lib/resource/resource-card.js';
import type { LayoutLoad } from './$types';

import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';
import type { CapabilityStatement } from 'fhir/r4';

export const load: LayoutLoad = async ({ fetch }) => {
  const capabilityStatement = await fetchJson<CapabilityStatement>(
    fetch,
    resolve('/metadata'),
    'error while loading the CapabilityStatement'
  );

  return {
    capabilityStatement: capabilityStatement,
    capabilityStatementObject: await fhirObject(capabilityStatement, fetch)
  };
};
