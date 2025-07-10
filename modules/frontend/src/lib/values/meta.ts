import type { Meta } from 'fhir/r4';

export function willBeRendered(value: Meta): boolean {
  return (value.profile !== undefined && value.profile.length > 0) || value.source !== undefined;
}
