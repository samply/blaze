import { base } from '$app/paths';
import { error } from '@sveltejs/kit';
import type { Stats } from './[dbId=id]/+page.js';

export interface Data {
	databases: Stats[];
}

export async function load({ fetch }): Promise<Data> {
	const res = await fetch(`${base}/__admin/dbs`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		throw error(res.status, {
			short: undefined,
			message: `An error happend while loading the list of database stats. Please try again later.`
		});
	}

	return { databases: await res.json() };
}
