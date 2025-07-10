import type { RequestHandler } from './$types';
import { base } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch, url }) => {
  const res = await fetch(`${base}/${params.type}/_history?${url.searchParams}`, {
    headers: { Accept: 'application/fhir+json' }
  });
  return new Response(await res.blob(), res);
};
