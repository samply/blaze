import type { HistoryBundle, Resource } from '../../../../fhir.js';
import { base } from '$app/paths';
import { error } from '@sveltejs/kit';
import { transformBundle } from '../../../../history/util.js';

export async function load({ fetch, params }) {
	const res = await fetch(`${base}/${params.type}/${params.id}/_history`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		throw error(res.status, `error while loading the ${params.type}/${params.id} history bundle`);
	}

	const bundle = (await res.json()) as HistoryBundle<Resource>;

	return { bundle: await transformBundle(fetch, bundle) };
}
