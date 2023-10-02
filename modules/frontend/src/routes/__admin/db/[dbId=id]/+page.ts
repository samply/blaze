import { base } from '$app/paths';
import { error } from '@sveltejs/kit';
import { toTitleCase } from '../../../../util.js';

type Fetch = typeof fetch;

export interface BlockCacheStats {
	capacity: number;
	usage: number;
}

export interface CompactionStats {
	pending: number;
	running: number;
}

export interface Stats {
	estimateLiveDataSize: number;
	usableSpace: number;
	blockCache: BlockCacheStats;
	compactions: CompactionStats;
}

export interface ColumnFamilyData {
	name: string;
	estimateNumKeys: number;
	estimateLiveDataSize: number;
	liveSstFilesSize: number;
	sizeAllMemTables: number;
}

export interface Data {
	stats: Stats;
	columnFamilies: ColumnFamilyData[];
}

async function loadStats(fetch: Fetch, dbId: string): Promise<Stats> {
	const res = await fetch(`${base}/__admin/db/${dbId}/stats`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		throw error(res.status, {
			short: res.status == 404 ? 'Not Found' : undefined,
			message:
				res.status == 404
					? `The ${toTitleCase(dbId)} database stats were not found.`
					: `An error happend while loading the ${toTitleCase(
							dbId
					  )} database stats. Please try again later.`
		});
	}

	return await res.json();
}

async function loadColumnFamilies(fetch: Fetch, dbId: string): Promise<ColumnFamilyData[]> {
	const res = await fetch(`${base}/__admin/db/${dbId}/column-families`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		throw error(res.status, {
			short: res.status == 404 ? 'Not Found' : undefined,
			message:
				res.status == 404
					? `The ${toTitleCase(dbId)} database column families were not found.`
					: `An error happend while loading the ${toTitleCase(
							dbId
					  )} database column families. Please try again later.`
		});
	}

	return await res.json();
}

export async function load({ fetch, params }): Promise<Data> {
	return {
		stats: await loadStats(fetch, params.dbId),
		columnFamilies: await loadColumnFamilies(fetch, params.dbId)
	};
}
