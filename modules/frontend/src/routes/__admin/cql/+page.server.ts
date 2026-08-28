import { backendUrl } from '$lib/backend.js';
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
  return {
    bloomFilters: await fetchJson<BloomFilter[]>(fetch, backendUrl('/__admin/cql/bloom-filters'), {
      error: 'An error happened while loading CQL Bloom filters. Please try again later.'
    })
  };
}
