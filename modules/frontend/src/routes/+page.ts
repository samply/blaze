import type { CapabilityStatement, Bundle, BundleEntry, Resource } from '../fhir';
import { bundleLink } from '../fhir';
import type { ResourceInfo } from './+layout';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

type Fetch = typeof fetch;

async function fetchResourceCountBundle(
	fetch: Fetch,
	capabilityStatement: CapabilityStatement
): Promise<Bundle<SearchSetBundle<Resource>>> {
	const resources = capabilityStatement.rest[0].resource;
	const bundle = {
		resourceType: 'Bundle',
		type: 'batch',
		entry: resources.map((r) => ({
			request: {
				url: `${r.type}?_summary=count`,
				method: 'GET'
			}
		}))
	};
	const res = await fetch(base, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/fhir+json',
			Accept: 'application/fhir+json'
		},
		body: JSON.stringify(bundle)
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, 'error while loading the resource counts');
	}

	return await res.json();
}

interface SearchSetBundle<T> extends Bundle<T> {
	link: { relation: string; url: string }[];
}

function transformEntry(entry: BundleEntry<SearchSetBundle<Resource>>): ResourceInfo | undefined {
	const bundle = entry.resource as SearchSetBundle<Resource>;
	const url = bundleLink(bundle, 'self')?.url;
	if (url === undefined) {
		return undefined;
	}
	const prefix = url.substring(0, url.indexOf('?'));
	return {
		name: prefix.substring(prefix.lastIndexOf('/') + 1),
		total: bundle.total ?? 0
	};
}

export async function load({ fetch, parent }) {
	const capabilityStatement = (await parent()).capabilityStatement;
	const resourceCountBundle = await fetchResourceCountBundle(fetch, capabilityStatement);
	return {
		resources:
			resourceCountBundle.entry !== undefined
				? resourceCountBundle.entry
						.map(transformEntry)
						.filter((e) => e !== undefined && e.total > 0)
						.map((e) => e as ResourceInfo)
						.sort((a: ResourceInfo, b: ResourceInfo) => b.total - a.total)
				: []
	};
}
