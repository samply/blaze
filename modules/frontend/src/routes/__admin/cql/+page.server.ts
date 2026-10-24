import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';

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
  const res = await fetch(backendUrl('/__admin/cql/bloom-filters'), {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status, `An error happened while loading CQL Bloom filters. Please try again later.`);
  }

  return { bloomFilters: await res.json() };
}
