import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';

export const GET: RequestHandler = async ({ fetch }) => {
  const res = await fetch(resolve('/metadata'), {
    headers: { Accept: 'application/fhir+json' }
  });
  return new Response(await res.blob(), res);
};
