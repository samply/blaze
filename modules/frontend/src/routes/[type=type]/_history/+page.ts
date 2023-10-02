import type { HistoryBundle, Resource } from '../../../fhir.js';
import { base } from '$app/paths';
import { error } from '@sveltejs/kit';
import { processParams } from '../../../util.js';
import { transformBundle } from '../../../history/util.js';

export async function load({ fetch, params, url }) {
	const res = await fetch(`${base}/${params.type}/_history?${processParams(url.searchParams)}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		throw error(res.status, `error while loading the ${params.type} history bundle`);
	}

	const bundle = (await res.json()) as HistoryBundle<Resource>;

	return { bundle: await transformBundle(fetch, bundle) };
}
