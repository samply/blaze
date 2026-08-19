import type { PageLoad, RouteParams } from './$types';

import { resolve } from '$app/paths';
import { toTitleCase } from '$lib/util.js';
import { fetchJson, notFoundError } from '$lib/fetch.js';

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
  name: string;
  estimateLiveDataSize: number;
  usableSpace: number;
  blockCache?: BlockCacheStats;
  compactions: CompactionStats;
}

export interface ColumnFamilyData {
  name: string;
  estimateNumKeys: number;
  liveSstFilesSize: number;
  sizeAllMemTables: number;
}

async function loadStats(fetch: Fetch, params: RouteParams): Promise<Stats> {
  return fetchJson<Stats>(
    fetch,
    resolve('/__admin/dbs/[dbId=id]/stats', params),
    (res) =>
      notFoundError(res, {
        notFound: `The ${toTitleCase(params.dbId)} database stats were not found.`,
        default: `An error happened while loading the ${toTitleCase(params.dbId)} database stats. Please try again later.`
      }),
    { Accept: 'application/json' }
  );
}

async function loadColumnFamilies(fetch: Fetch, params: RouteParams): Promise<ColumnFamilyData[]> {
  return fetchJson<ColumnFamilyData[]>(
    fetch,
    resolve('/__admin/dbs/[dbId=id]/column-families', params),
    (res) =>
      notFoundError(res, {
        notFound: `The ${toTitleCase(params.dbId)} database column families were not found.`,
        default: `An error happened while loading the ${toTitleCase(params.dbId)} database column families. Please try again later.`
      }),
    { Accept: 'application/json' }
  );
}

export const load: PageLoad = async ({ data, fetch, params }) => {
  return {
    ...data,
    stats: await loadStats(fetch, params),
    columnFamilies: await loadColumnFamilies(fetch, params)
  };
};
