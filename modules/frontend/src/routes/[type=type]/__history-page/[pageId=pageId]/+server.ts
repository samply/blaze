import type { RequestHandler } from './$types';
import { base } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const res = await fetch(`${base}/${params.type}/__history-page/${params.pageId}`, {
    headers: { Accept: 'application/fhir+json' }
  });
  return new Response(await res.blob(), res);
};
