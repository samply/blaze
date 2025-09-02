import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';
import type { CapabilityStatement } from 'fhir/r5';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const res = await fetch(resolve('/metadata'), { headers: { Accept: 'application/fhir+json' } });

  if (!res.ok) {
    return json({ msg: 'error while loading the CapabilityStatement' });
  }

  const capabilityStatement: CapabilityStatement = await res.json();

  const server = capabilityStatement.rest?.at(0);
  const resource = server?.resource?.find((r) => r.type == params.type);

  return json({
    searchIncludes: resource?.searchInclude
  });
};
