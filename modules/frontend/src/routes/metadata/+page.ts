import { fhirObject } from '../../resource/resource-card.js';

export async function load({ fetch, parent }) {
	const capabilityStatement = (await parent()).capabilityStatement;

	return { capabilityStatement: await fhirObject(capabilityStatement, fetch) };
}
