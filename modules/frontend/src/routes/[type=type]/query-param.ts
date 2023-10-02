import type { SearchParamType } from '../../fhir.js';

export interface QueryParam {
	id: number;
	active: boolean;
	name: string;
	type: SearchParamType;
	value: string;
}
