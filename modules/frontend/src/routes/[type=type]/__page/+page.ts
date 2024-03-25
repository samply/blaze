import { fetchPageBundleWithDuration } from '../util.js';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';

export async function load({ fetch, params, url }) {
	const res = await fetch(`${base}/${params.type}/__search-params`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, 'error while fetching the search params');
	}

	return {
		searchParams: (await res.json()).searchParams as CapabilityStatementRestResourceSearchParam[],
		streamed: {
			start: Date.now(),
			bundle: fetchPageBundleWithDuration(fetch, params, url)
		}
	};
}
