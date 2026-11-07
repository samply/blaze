import type { Parameters } from 'fhir/r4';

export interface ResourceTypeTotal {
  name: string;
  total: number;
}

/** Extracts the resource type totals from the result of the `$totals` operation. */
export function resourceTypeTotals(parameters: Parameters): ResourceTypeTotal[] {
  return (
    parameters.parameter?.map((p) => ({
      name: p.name,
      total: p.valueUnsignedInt || 0
    })) || []
  );
}

/** Returns a copy of `totals` sorted by total descending, and by name on equal totals. */
export function rankedByTotal(totals: ResourceTypeTotal[]): ResourceTypeTotal[] {
  return [...totals].sort((a, b) => b.total - a.total || a.name.localeCompare(b.name));
}

/** Returns the sum of all `totals`. */
export function grandTotal(totals: ResourceTypeTotal[]): number {
  return totals.reduce((sum, { total }) => sum + total, 0);
}

/** Returns the total of the resource type Patient. */
export function patientTotal(totals: ResourceTypeTotal[]): number {
  return totals.find(({ name }) => name === 'Patient')?.total ?? 0;
}
