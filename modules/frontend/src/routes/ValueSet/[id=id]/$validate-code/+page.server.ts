import type { Actions, PageServerLoad } from './$types';
import type { Bundle, OperationOutcome, Parameters, ParametersParameter, ValueSet } from 'fhir/r4';
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
    const code = data.get('code') as string;
    const system = data.get('system') as string;
    const systemVersion = data.get('systemVersion') as string;
    const display = data.get('display') as string;
    const displayLanguage = data.get('displayLanguage') as string;
    const inferSystem = Boolean(data.get('inferSystem'));

    const parameters: ParametersParameter[] = [
      {
        name: 'code',
        valueCode: code
      }
    ];

    if (system !== '') {
      parameters.push({
        name: 'system',
        valueString: system
      });
    }

    if (systemVersion !== '') {
      parameters.push({
        name: 'systemVersion',
        valueString: systemVersion
      });
    }

    if (display !== '') {
      parameters.push({
        name: 'display',
        valueString: display
      });
    }

    if (displayLanguage !== '') {
      parameters.push({
        name: 'displayLanguage',
        valueCode: displayLanguage
      });
    }

    if (inferSystem) {
      parameters.push({
        name: 'inferSystem',
        valueBoolean: true
      });
    }

    const res = await fetch(resolve('/ValueSet/[id=id]/$validate-code', params), {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify({
        resourceType: 'Parameters',
        parameter: parameters
      })
    });

    const result = {
      code,
      system,
      systemVersion,
      display,
      displayLanguage,
      inferSystem
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
      result: (await res.json()) as Parameters
    };
  }
} satisfies Actions;
