import type { HistoryBundle, Resource } from '../../fhir.js';
import type { FhirObject } from '../../resource/resource-card';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '../../util.js';
import { transformBundle } from '../../history/util.js';

export interface Data {
	bundle: HistoryBundle<FhirObject>;
}

export async function load({ fetch, url }): Promise<Data> {
	const res = await fetch(`${base}/_history?${processParams(url.searchParams)}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, {
			message: 'An error happened while loading the history. Please try again later.'
		});
	}

	const bundle = (await res.json()) as HistoryBundle<Resource>;

	return { bundle: await transformBundle(fetch, bundle) };
}
