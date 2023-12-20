import type { HistoryBundle, Resource } from '../../fhir';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '../../util';
import { transformBundle } from '../../history/util';

export async function load({ fetch, url }) {
	const res = await fetch(`${base}/_history?${processParams(url.searchParams)}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, {
			message: 'An error happend while loading the history. Please try again later.'
		});
	}

	const bundle = (await res.json()) as HistoryBundle<Resource>;

	return { bundle: await transformBundle(fetch, bundle) };
}
