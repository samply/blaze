import type { PageServerLoad } from './$types';

import { backendUrl } from '$lib/backend.js';
import { fetchJson, loadError } from '$lib/fetch.js';
import { toTitleCase } from '$lib/util.js';
import { pascalCase } from 'change-case';

export interface Level {
  level: number;
  fileSize: number;
  numFiles: number;
}

export interface Data {
  name: string;
  fileSize: number;
  numFiles: number;
  levels: Level[];
}

export const load: PageServerLoad = async ({ fetch, params }) => {
  const columnFamily = pascalCase(params.cfId);
  const database = toTitleCase(params.dbId);

  return fetchJson<Data>(
    fetch,
    backendUrl(`/__admin/dbs/${params.dbId}/column-families/${params.cfId}/metadata`),
    {
      error: loadError({
        404: `The column family ${columnFamily} was not found in database ${database}.`,
        default: `An error happened while loading the column family ${columnFamily} of database ${database}. Please try again later.`
      })
    }
  );
};
