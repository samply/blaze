import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const res = await fetch(resolve('/__admin/dbs/[dbId=id]/stats', params), {
    headers: { Accept: 'application/json' }
  });
  return new Response(await res.blob(), res);
};
