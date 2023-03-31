import type { SearchParamType } from '../../fhir';

export interface QueryParam {
	id: number;
	active: boolean;
	name: string;
	type: SearchParamType;
	value: string;
}
