import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';

export interface BloomFilter {
  hash: string;
  t: number;
  patientCount: number;
  exprForm: string;
  memSize: number;
}

export interface Data {
  bloomFilters: BloomFilter[];
}

export async function load({ fetch }): Promise<Data> {
  const bloomFilters = await fetchJson<BloomFilter[]>(
    fetch,
    resolve('/__admin/cql/bloom-filters'),
    'An error happened while loading CQL Bloom filters. Please try again later.',
    { Accept: 'application/json' }
  );

  return { bloomFilters };
}
