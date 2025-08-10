import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';

export const GET: RequestHandler = async ({ fetch }) => {
  const res = await fetch(resolve('/__admin/dbs'), {
    headers: { Accept: 'application/json' }
  });
  return new Response(await res.blob(), res);
};
