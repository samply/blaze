import type { LayoutServerLoad } from './$types';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import type { CapabilityStatement } from 'fhir/r4';

export const load: LayoutServerLoad = async ({ fetch }) => {
  const res = await fetch(backendUrl('/metadata'), {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status, 'error while loading the CapabilityStatement');
  }

  return {
    capabilityStatement: (await res.json()) as CapabilityStatement
  };
};
