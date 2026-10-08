import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { fetchJson } from '$lib/fetch.js';
import type { Stats } from './[dbId=id]/+page.server.js';

export const load: PageServerLoad = async ({ fetch }) => {
  return {
    databases: await fetchJson<Stats[]>(fetch, backendUrl('/__admin/dbs'), {
      error: 'An error happened while loading the list of database stats. Please try again later.'
    })
  };
};
