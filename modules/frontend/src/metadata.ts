import type { StructureDefinition } from './fhir.js';
import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

const structureDefinitionStore = new Map<string, Promise<StructureDefinition>>();

function structureDefinitionUrl(type: string) {
	return `${base}/__metadata/StructureDefinition?url=http://hl7.org/fhir/StructureDefinition/${type}`;
}

async function loadStructureDefinition(fetch: typeof window.fetch, type: string) {
	const res = await fetch(structureDefinitionUrl(type), {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(
			res.status as NumericRange<400, 599>,
			`error while loading the ${type} StructureDefinition`
		);
	}

	return (await res.json()) as StructureDefinition;
}

export async function fetchStructureDefinition(
	type: string,
	fetch: typeof window.fetch = window.fetch
) {
	const cached = structureDefinitionStore.get(type);
	if (cached) {
		return cached;
	}

	const load = loadStructureDefinition(fetch, type);

	structureDefinitionStore.set(type, load);

	return load;
}
