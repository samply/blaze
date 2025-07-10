import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { base } from '$app/paths';
import type { CapabilityStatement } from 'fhir/r4';
import { sortByProperty } from '$lib/util';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const res = await fetch(`${base}/metadata`, { headers: { Accept: 'application/fhir+json' } });

  if (!res.ok) {
    return json({ msg: 'error while loading the CapabilityStatement' }, { status: res.status });
  }

  const capabilityStatement: CapabilityStatement = await res.json();

  const server = capabilityStatement.rest?.at(0);
  const resource = server?.resource?.find((r) => r.type == params.type);

  return json({
    searchParams: [...(server?.searchParam || []), ...(resource?.searchParam || [])].sort(
      sortByProperty('name')
    )
  });
};
