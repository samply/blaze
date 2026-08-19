import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { toTitleCase } from '$lib/util.js';
import { pascalCase } from 'change-case';
import { fetchJson, notFoundError } from '$lib/fetch.js';

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

export const load: PageLoad = async ({ fetch, params }) => {
  return fetchJson<Data>(
    fetch,
    resolve('/__admin/dbs/[dbId=id]/column-families/[cfId=id]/metadata', params),
    (res) =>
      notFoundError(res, {
        notFound: `The column family ${pascalCase(params.cfId)} was not found in database ${toTitleCase(params.dbId)}.`,
        default: `An error happened while loading the column family ${pascalCase(params.cfId)} of database ${toTitleCase(params.dbId)}. Please try again later.`
      }),
    { Accept: 'application/json' }
  );
};
