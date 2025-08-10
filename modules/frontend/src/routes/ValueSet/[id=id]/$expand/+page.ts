import type { PageLoad } from './$types';
import type { Bundle, ValueSet } from 'fhir/r4';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

export const load: PageLoad = async ({ fetch, params }) => {
  const res = await fetch(
    `${resolve('/ValueSet')}?_id=${params.id}&_elements=version,title,description`,
    {
      headers: {
        Accept: 'application/fhir+json'
      }
    }
  );

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : res.status == 410 ? 'Gone' : undefined,
      message:
        res.status == 404
          ? `The ValueSet with ID ${params.id} was not found.`
          : res.status == 410
            ? `The ValueSet with ID ${params.id} was deleted. Please look into the history.`
            : `An error happened while loading the ValueSet with ID ${params.id}. Please try again later.`
    });
  }

  const bundle: Bundle = await res.json();

  return {
    valueSet: bundle.entry?.[0].resource as ValueSet
  };
};
