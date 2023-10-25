import type { Meta } from '../fhir.js';

export function willBeRendered(value: Meta): boolean {
	return (value.profile !== undefined && value.profile.length > 0) || value.source !== undefined;
}
