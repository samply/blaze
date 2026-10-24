import type { PageServerLoad } from './$types';

import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import { toTitleCase } from '#lib/util.js';
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
  const res = await fetch(
    backendUrl(`/__admin/dbs/${params.dbId}/column-families/${params.cfId}/metadata`),
    {
      headers: { Accept: 'application/json' }
    }
  );

  if (!res.ok) {
    error(
      res.status,
      res.status == 404
        ? `The column family ${pascalCase(params.cfId)} was not found in database ${toTitleCase(
            params.dbId
          )}.`
        : `An error happened while loading the column family ${pascalCase(
            params.cfId
          )} of database ${toTitleCase(params.dbId)}. Please try again later.`,
      { short: res.status == 404 ? 'Not Found' : undefined }
    );
  }

  return (await res.json()) as Data;
};
