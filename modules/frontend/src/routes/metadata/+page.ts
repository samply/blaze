import { fhirObject } from '../../resource/resource-card';

export async function load({ fetch, parent }) {
	const capabilityStatement = (await parent()).capabilityStatement;

	return { capabilityStatement: await fhirObject(capabilityStatement, fetch) };
}
