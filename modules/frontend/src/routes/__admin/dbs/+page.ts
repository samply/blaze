import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';
import type { Stats } from './[dbId=id]/+page.js';

export const load: PageLoad = async ({ fetch }) => {
  const databases = await fetchJson<Stats[]>(
    fetch,
    resolve('/__admin/dbs'),
    'An error happened while loading the list of database stats. Please try again later.',
    { Accept: 'application/json' }
  );

  return { databases };
};
