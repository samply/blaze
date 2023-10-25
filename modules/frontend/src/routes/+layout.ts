import type { CapabilityStatement } from '../fhir.js';
import { base } from '$app/paths';
import { error } from '@sveltejs/kit';

export const ssr = false;

export interface ResourceInfo {
	name: string;
	total: number;
}

export async function load({ fetch }) {
	const res = await fetch(`${base}/metadata`, { headers: { Accept: 'application/fhir+json' } });

	if (!res.ok) {
		throw error(res.status, 'error while loading the CapabilityStatement');
	}

	return { capabilityStatement: (await res.json()) as CapabilityStatement };
}
