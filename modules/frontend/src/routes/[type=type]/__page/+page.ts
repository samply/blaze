import { fetchBundleWithDuration } from '../util.js';
import { fetchStructureDefinition } from '$lib/metadata.js';

export async function load({ fetch, params, url }) {
	fetchStructureDefinition(params.type, fetch);

	return {
		streamed: {
			start: Date.now(),
			bundle: fetchBundleWithDuration(fetch, params, url)
		}
	};
}
