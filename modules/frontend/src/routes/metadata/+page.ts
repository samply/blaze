import { type FhirObject, fhirObject } from '../../resource/resource-card.js';

export interface Data {
	capabilityStatement: FhirObject;
}

export async function load({ fetch, parent }): Promise<Data> {
	const capabilityStatement = (await parent()).capabilityStatement;

	return { capabilityStatement: await fhirObject(capabilityStatement, fetch) };
}
