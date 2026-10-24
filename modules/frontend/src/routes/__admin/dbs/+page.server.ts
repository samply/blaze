import type { PageServerLoad } from './$types';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import type { Stats } from './[dbId=id]/+page.server.js';

export const load: PageServerLoad = async ({ fetch }) => {
  const res = await fetch(backendUrl('/__admin/dbs'), {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(
      res.status,
      `An error happened while loading the list of database stats. Please try again later.`
    );
  }

  return { databases: (await res.json()) as Stats[] };
};
