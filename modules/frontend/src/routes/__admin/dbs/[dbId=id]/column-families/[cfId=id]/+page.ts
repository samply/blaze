import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
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

export const load: PageLoad = async ({ fetch, params }) => {
  const res = await fetch(
    resolve('/__admin/dbs/[dbId=id]/column-families/[cfId=id]/metadata', params),
    {
      headers: { Accept: 'application/json' }
    }
  );

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : undefined,
      message:
        res.status == 404
          ? `The column family ${pascalCase(params.cfId)} was not found in database ${toTitleCase(
              params.dbId
            )}.`
          : `An error happened while loading the column family ${pascalCase(
              params.cfId
            )} of database ${toTitleCase(params.dbId)}. Please try again later.`
    });
  }

  return (await res.json()) as Data;
};
