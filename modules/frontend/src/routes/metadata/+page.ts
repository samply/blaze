import { fhirObject } from '$lib/resource/resource-card.js';
import type { PageLoad } from './$types';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

export const load: PageLoad = async ({ fetch }) => {
	const res = await fetch(`${base}/metadata`, { headers: { Accept: 'application/fhir+json' } });

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, 'error while loading the CapabilityStatement');
	}

	return { capabilityStatement: await fhirObject(await res.json(), fetch) };
};
