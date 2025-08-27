import type { Parameters } from 'fhir/r5';
import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
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
  const res = await fetch(resolve('/$totals'), { headers: { Accept: 'application/fhir+json' } });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, 'error while executing the $totals operation');
  }

  return { resourceTypeTotals: resourceTypeTotals(await res.json()) };
};
