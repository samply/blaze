import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch, url }) => {
  const res = await fetch(
    `${resolve('/[type=type]/[id=id]/$graph', params)}?graph=${url.searchParams.get('graph')}`,
    {
      headers: { Accept: 'application/fhir+json' }
    }
  );
  return new Response(await res.blob(), res);
};
