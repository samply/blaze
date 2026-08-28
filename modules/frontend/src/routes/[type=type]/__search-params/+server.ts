import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { resolve } from '$app/paths';
import type { CapabilityStatement } from 'fhir/r4';
import { searchMetadata } from '$lib/search-metadata.js';

export const GET: RequestHandler = async ({ params, fetch }) => {
  const res = await fetch(resolve('/metadata'), { headers: { Accept: 'application/fhir+json' } });

  if (!res.ok) {
    return json({ msg: 'error while loading the CapabilityStatement' }, { status: res.status });
  }

  const capabilityStatement: CapabilityStatement = await res.json();

  return json(searchMetadata(capabilityStatement, params.type));
};
