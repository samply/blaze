import type { Actions, PageServerLoad } from './$types';
import type { Bundle, OperationOutcome, Parameters, ParametersParameter, ValueSet } from 'fhir/r4';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir, resourceError } from '$lib/fetch.js';
import { fail } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const bundle = await fetchFhir<Bundle>(
    fetch,
    backendUrl('/ValueSet', `_id=${params.id}&_elements=version,title,description`),
    { error: resourceError('ValueSet', params.id) }
  );

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

    const res = await fetch(backendUrl(`/ValueSet/${params.id}/$validate-code`), {
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
