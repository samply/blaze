import { fetchBundle } from './util.js';
import { fetchStructureDefinition } from '../../metadata.js';

export async function load({ fetch, params, url }) {
	fetchStructureDefinition(params.type, fetch);

	return {
		streamed: {
			start: Date.now(),
			bundle: fetchBundle(fetch, params, url)
		}
	};
}
