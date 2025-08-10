import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const res = await fetch(resolve('/[type=type]/__history-page/[pageId=pageId]', params), {
    headers: { Accept: 'application/fhir+json' }
  });
  return new Response(await res.blob(), res);
};
