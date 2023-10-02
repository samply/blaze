import { base } from '$app/paths';
import { error } from '@sveltejs/kit';

export interface BloomFilter {
	t: number;
	patientCount: number;
	exprForm: string;
	memSize: number;
}

export interface Data {
	bloomFilters: BloomFilter[];
}

export async function load({ fetch }): Promise<Data> {
	const res = await fetch(`${base}/__admin/cql/bloom-filters`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		throw error(res.status, {
			short: undefined,
			message: `An error happend while loading CQL Bloom filters. Please try again later.`
		});
	}

	return { bloomFilters: await res.json() };
}
