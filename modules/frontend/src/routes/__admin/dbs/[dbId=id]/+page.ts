import type { PageLoad } from './$types';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { toTitleCase } from '$lib/util.js';

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

async function loadStats(fetch: Fetch, dbId: string): Promise<Stats> {
  const res = await fetch(`${base}/__admin/dbs/${dbId}/stats`, {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : undefined,
      message:
        res.status == 404
          ? `The ${toTitleCase(dbId)} database stats were not found.`
          : `An error happened while loading the ${toTitleCase(
              dbId
            )} database stats. Please try again later.`
    });
  }

  return await res.json();
}

async function loadColumnFamilies(fetch: Fetch, dbId: string): Promise<ColumnFamilyData[]> {
  const res = await fetch(`${base}/__admin/dbs/${dbId}/column-families`, {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : undefined,
      message:
        res.status == 404
          ? `The ${toTitleCase(dbId)} database column families were not found.`
          : `An error happened while loading the ${toTitleCase(
              dbId
            )} database column families. Please try again later.`
    });
  }

  return await res.json();
}

export const load: PageLoad = async ({ fetch, params }) => {
  return {
    stats: await loadStats(fetch, params.dbId),
    columnFamilies: await loadColumnFamilies(fetch, params.dbId)
  };
};
