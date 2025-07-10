import type { RequestHandler } from './$types';
import { base } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch, url }) => {
  const res = await fetch(
    `${base}/${params.type}/${params.id}/$graph?graph=${url.searchParams.get('graph')}`,
    {
      headers: { Accept: 'application/fhir+json' }
    }
  );
  return new Response(await res.blob(), res);
};
