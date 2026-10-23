import { fhirObject } from '$lib/resource/resource-card.js';
import type { LayoutServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import type { CapabilityStatement } from 'fhir/r4';

export const load: LayoutServerLoad = async ({ fetch }) => {
  const capabilityStatement = await fetchFhir<CapabilityStatement>(fetch, backendUrl('/metadata'), {
    error: 'error while loading the CapabilityStatement'
  });

  return {
    capabilityStatement: capabilityStatement,
    capabilityStatementObject: await fhirObject(capabilityStatement, fetch)
  };
};
