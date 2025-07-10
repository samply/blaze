import type { PageLoad } from './$types';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import type { Stats } from './[dbId=id]/+page.js';

export const load: PageLoad = async ({ fetch }) => {
  const res = await fetch(`${base}/__admin/dbs`, {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: undefined,
      message: `An error happened while loading the list of database stats. Please try again later.`
    });
  }

  return { databases: (await res.json()) as Stats[] };
};
