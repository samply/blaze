import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch, url }) => {
  const res = await fetch(`${resolve('/[type=type]/[id=id]', params)}?${url.searchParams}`, {
    headers: { Accept: 'application/fhir+json' }
  });
  return new Response(await res.blob(), res);
};
