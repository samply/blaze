import type { Bundle, BundleLink, CodeableConcept, Coding } from 'fhir/r4';

export enum SearchParamType {
	number = 'number',
	date = 'date',
	string = 'string',
	token = 'token',
	reference = 'reference',
	composite = 'composite',
	quantity = 'quantity',
	uri = 'uri',
	special = 'special'
}

export enum RestfulInteraction {
	read = 'read',
	vread = 'vread',
	update = 'update',
	patch = 'patch',
	delete = 'delete',
	historyInstance = 'history-instance',
	historyType = 'history-type',
	create = 'create',
	searchType = 'search-type'
}

export function bundleLink(bundle: Bundle, relation: string): BundleLink | undefined {
	return bundle.link?.filter((l) => l.relation == relation)[0];
}

export function coding(concept: CodeableConcept, system: string): Coding | undefined {
	return concept.coding?.filter((c) => c.system == system)[0];
}
