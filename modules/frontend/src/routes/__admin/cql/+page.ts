import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

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
  const res = await fetch(`${base}/__admin/cql/bloom-filters`, {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: undefined,
      message: `An error happened while loading CQL Bloom filters. Please try again later.`
    });
  }

  return { bloomFilters: await res.json() };
}
