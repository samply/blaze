import type { Parameters } from 'fhir/r4';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import type { PageServerLoad } from './$types';

interface ResourceTypeTotal {
  name: string;
  total: number;
}

function resourceTypeTotals(parameters: Parameters): ResourceTypeTotal[] {
  return (
    parameters.parameter?.map((p) => ({
      name: p.name,
      total: p.valueUnsignedInt || 0
    })) || []
  );
}

export interface Data {
  resourceTypeTotals: ResourceTypeTotal[];
}

export const load: PageServerLoad = async ({ fetch }) => {
  const parameters = await fetchFhir<Parameters>(fetch, backendUrl('/$totals'), {
    error: 'error while executing the $totals operation'
  });

  return { resourceTypeTotals: resourceTypeTotals(parameters) };
};
