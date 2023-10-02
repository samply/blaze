import { base } from '$app/paths';
import { error } from '@sveltejs/kit';
import { toTitleCase } from '../../../../../../util.js';
import { pascalCase } from 'change-case';

export interface Level {
	level: number;
	size: number;
	numFiles: number;
}

export interface Data {
	name: string;
	size: number;
	numFiles: number;
	levels: Level[];
}

export async function load({ fetch, params }): Promise<Data> {
	const res = await fetch(
		`${base}/__admin/dbs/${params.dbId}/column-families/${params.cfId}/metadata`,
		{
			headers: { Accept: 'application/json' }
		}
	);

	if (!res.ok) {
		throw error(res.status, {
			short: res.status == 404 ? 'Not Found' : undefined,
			message:
				res.status == 404
					? `The column family ${pascalCase(params.cfId)} was not found in database ${toTitleCase(
							params.dbId
					  )}.`
					: `An error happend while loading the column family ${pascalCase(
							params.cfId
					  )} of database ${toTitleCase(params.dbId)}. Please try again later.`
		});
	}

	return await res.json();
}
