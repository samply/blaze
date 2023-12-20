import type { SearchSetBundle, SearchSetBundleEntry, Resource, OperationOutcome } from '../../fhir';
import type { RouteParams } from './$types';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '../../util';
import { fhirObject, type FhirObject } from '../../resource/resource-card';

export async function transformBundle(
	fetch: typeof window.fetch,
	bundle: SearchSetBundle<Resource>
): Promise<SearchSetBundle<FhirObject>> {
	return bundle.entry !== undefined
		? {
				...bundle,
				entry: await Promise.all(
					bundle.entry.map(async (e: SearchSetBundleEntry<Resource>) => ({
						...e,
						resource: await fhirObject(e.resource, fetch)
					}))
				)
			}
		: (bundle as SearchSetBundle<unknown> as SearchSetBundle<FhirObject>);
}

async function outcome(res: Response): Promise<OperationOutcome> {
	return (await res.json()) as OperationOutcome;
}

export async function appError(params: RouteParams, res: Response) {
	switch (res.status) {
		case 400:
			return {
				short: 'Bad Request',
				message: (await outcome(res)).issue[0].diagnostics ?? 'Please check your search params.'
			};
		case 422:
			return {
				short: 'Unprocessable Content',
				message: (await outcome(res)).issue[0].diagnostics ?? 'Please check your search params.'
			};
		case 404:
			return {
				short: 'Not Found',
				message: `The resource type ${params.type} was not found.`
			};
		default:
			return {
				short: undefined,
				message: `An error happend while loading the ${params.type}s. Please try again later.`
			};
	}
}

export async function fetchBundle(fetch: typeof window.fetch, params: RouteParams, url: URL) {
	const start = Date.now();

	const res = await fetch(`${base}/${params.type}?${processParams(url.searchParams)}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, await appError(params, res));
	}

	const bundle = (await res.json()) as SearchSetBundle<Resource>;

	bundle.duration = Date.now() - start;

	return transformBundle(fetch, bundle);
}
