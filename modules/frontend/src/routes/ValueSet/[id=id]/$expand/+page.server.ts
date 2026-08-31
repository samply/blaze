import type { Actions, PageServerLoad } from './$types';
import type { Bundle, OperationOutcome, ParametersParameter, ValueSet } from 'fhir/r4';
import { base, resolve } from '$app/paths';
import { error, fail, type NumericRange } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(`${base}/ValueSet?_id=${params.id}&_elements=version,title,description`, {
    headers: {
      Accept: 'application/fhir+json'
    }
  });

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

export const actions = {
  default: async ({ request, fetch, params }) => {
    const data = await request.formData();
    const filter = data.get('filter') as string;
    const property = data.get('property') as string;
    const displayLanguage = data.get('displayLanguage') as string;
    const systemVersion = data.get('systemVersion') as string;
    const includeDesignations = Boolean(data.get('includeDesignations'));
    const includeDefinition = Boolean(data.get('includeDefinition'));
    const activeOnly = Boolean(data.get('activeOnly'));
    const excludeNested = Boolean(data.get('excludeNested'));

    const parameters: ParametersParameter[] = [
      {
        name: 'count',
        valueInteger: 100
      }
    ];

    if (filter !== '') {
      parameters.push({
        name: 'filter',
        valueString: filter
      });
    }

    if (property !== '') {
      parameters.push({
        name: 'property',
        valueString: property
      });
    }

    if (displayLanguage !== '') {
      parameters.push({
        name: 'displayLanguage',
        valueCode: displayLanguage
      });
    }

    if (systemVersion !== '') {
      parameters.push({
        name: 'system-version',
        valueString: systemVersion
      });
    }

    if (includeDesignations) {
      parameters.push({
        name: 'includeDesignations',
        valueBoolean: true
      });
    }

    if (includeDefinition) {
      parameters.push({
        name: 'includeDefinition',
        valueBoolean: true
      });
    }

    if (activeOnly) {
      parameters.push({
        name: 'activeOnly',
        valueBoolean: true
      });
    }

    if (excludeNested) {
      parameters.push({
        name: 'excludeNested',
        valueBoolean: true
      });
    }

    const res = await fetch(resolve('/ValueSet/[id=id]/$expand', params), {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify({
        resourceType: 'Parameters',
        parameter: parameters
      })
    });

    const result = {
      filter,
      property,
      displayLanguage,
      systemVersion,
      includeDesignations,
      includeDefinition,
      activeOnly,
      excludeNested
    };

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        ...result,
        incorrect: true,
        msg: error.issue?.[0]?.diagnostics ?? error.issue?.[0]?.details?.text
      });
    }

    return {
      ...result,
      valueSet: (await res.json()) as ValueSet
    };
  }
} satisfies Actions;
