import type { HistoryBundle, Resource } from '../../../../fhir';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { transformBundle } from '../../../../history/util';

export async function load({ fetch, params }) {
	const res = await fetch(`${base}/${params.type}/${params.id}/_history`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(
			res.status as NumericRange<400, 599>,
			`error while loading the ${params.type}/${params.id} history bundle`
		);
	}

	const bundle = (await res.json()) as HistoryBundle<Resource>;

	return { bundle: await transformBundle(fetch, bundle) };
}
