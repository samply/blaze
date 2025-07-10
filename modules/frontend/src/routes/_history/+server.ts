import type { RequestHandler } from './$types';
import { base } from '$app/paths';

export const GET: RequestHandler = async ({ fetch, url }) => {
  const res = await fetch(`${base}/_history?${url.searchParams}`, {
    headers: { Accept: 'application/fhir+json' }
  });
  return new Response(await res.blob(), res);
};
