import { base } from '$app/paths';
import { error } from '@sveltejs/kit';
import { fhirObject } from '../../../../../resource/resource-card.js';

export async function load({ fetch, params }) {
	const res = await fetch(`${base}/${params.type}/${params.id}/_history/${params.vid}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		throw error(res.status, {
			short: res.status == 404 ? 'Not Found' : undefined,
			message:
				res.status == 404
					? `The ${params.type} with ID ${params.id} and version ${params.vid} was not found.`
					: `An error happend while loading the ${params.type} with ID ${params.id} and version ${params.vid}. Please try again later.`
		});
	}

	const resource = await res.json();

	return {
		resource: await fhirObject(resource, fetch)
	};
}
