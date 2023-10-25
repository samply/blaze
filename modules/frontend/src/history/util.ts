import type { HistoryBundle, HistoryBundleEntry, Resource } from '../fhir.js';
import { fhirObject, type FhirObject } from '../resource/resource-card.js';

export async function transformBundle(
	fetch: typeof window.fetch,
	bundle: HistoryBundle<Resource>
): Promise<HistoryBundle<FhirObject>> {
	return bundle.entry !== undefined
		? {
				...bundle,
				entry: await Promise.all(
					bundle.entry.map(async (e: HistoryBundleEntry<Resource>) =>
						e.resource !== undefined
							? { ...e, resource: await fhirObject(e.resource, fetch) }
							: (e as HistoryBundleEntry<unknown> as HistoryBundleEntry<FhirObject>)
					)
				)
		  }
		: (bundle as HistoryBundle<unknown> as HistoryBundle<FhirObject>);
}
