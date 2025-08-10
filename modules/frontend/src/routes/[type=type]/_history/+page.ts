import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '$lib/util.js';
import { transformBundle } from '$lib/resource/resource-card.js';

export const load: PageLoad = async ({ fetch, params, url }) => {
  const res = await fetch(
    `${resolve('/[type=type]/_history', params)}?${processParams(url.searchParams)}`,
    {
      headers: { Accept: 'application/fhir+json' }
    }
  );

  if (!res.ok) {
    error(
      res.status as NumericRange<400, 599>,
      `error while loading the ${params.type} history bundle`
    );
  }

  return { bundle: await transformBundle(fetch, await res.json()) };
};
