import type { PageLoad } from './$types';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { transformBundle } from '$lib/resource/resource-card.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const res = await fetch(`${base}/${params.type}/${params.id}/_history?_count&_summary=true`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(
      res.status as NumericRange<400, 599>,
      `error while loading the ${params.type}/${params.id} history bundle`
    );
  }

  return { bundle: await transformBundle(fetch, await res.json()) };
};
