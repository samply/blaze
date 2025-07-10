import type { SearchParamType } from '$lib/fhir.js';

export interface QueryParam {
  id: number;
  active: boolean;
  name: string;
  type: SearchParamType;
  value: string;
}
