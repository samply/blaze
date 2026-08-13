import type { Parameters } from 'fhir/r4';
import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';
import type { PageLoad } from './$types';

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

export const load: PageLoad = async ({ fetch }) => {
  const parameters = await fetchJson<Parameters>(
    fetch,
    resolve('/$totals'),
    'error while executing the $totals operation'
  );

  return { resourceTypeTotals: resourceTypeTotals(parameters) };
};
