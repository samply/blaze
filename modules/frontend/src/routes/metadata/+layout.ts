import { fhirObject } from '$lib/resource/resource-card.js';
import type { LayoutLoad } from './$types';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import type { CapabilityStatement } from 'fhir/r4';

export const load: LayoutLoad = async ({ fetch }) => {
  const res = await fetch(`${base}/metadata`, { headers: { Accept: 'application/fhir+json' } });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, 'error while loading the CapabilityStatement');
  }

  const capabilityStatement = (await res.json()) as CapabilityStatement;

  return {
    capabilityStatement: capabilityStatement,
    capabilityStatementObject: await fhirObject(capabilityStatement, fetch)
  };
};
