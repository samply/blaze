import type { PageLoad } from './$types';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '$lib/util.js';
import { transformBundle } from '$lib/resource/resource-card.js';

export const load: PageLoad = async ({ fetch, url }) => {
  const res = await fetch(`${resolve('/_history')}?${processParams(url.searchParams)}`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      message: 'An error happened while loading the history. Please try again later.'
    });
  }

  return { bundle: await transformBundle(fetch, await res.json()) };
};
