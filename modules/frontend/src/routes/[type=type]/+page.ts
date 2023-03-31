import { fetchBundle } from './util';
import { fetchStructureDefinition } from '../../metadata';

export async function load({ fetch, params, url }) {
	fetchStructureDefinition(params.type, fetch);

	return {
		streamed: {
			start: Date.now(),
			bundle: fetchBundle(fetch, params, url)
		}
	};
}
