import type { PageLoad } from './$types';
import type { Bundle, ValueSet } from 'fhir/r4';

import { resolve } from '$app/paths';
import { fetchJson, resourceNotFoundError } from '$lib/fetch.js';

export const load: PageLoad = async ({ fetch, params }) => {
  const bundle = await fetchJson<Bundle>(
    fetch,
    `${resolve('/ValueSet')}?_id=${params.id}&_elements=version,title,description`,
    (res) => resourceNotFoundError(res, 'ValueSet', params.id)
  );

  return {
    valueSet: bundle.entry?.[0].resource as ValueSet
  };
};
