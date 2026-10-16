import type { Actions, PageServerLoad } from './$types';
import type {
  Bundle,
  CodeSystem,
  OperationOutcome,
  Parameters,
  ParametersParameter
} from 'fhir/r4';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir, resourceError } from '$lib/fetch.js';
import { fail } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const bundle = await fetchFhir<Bundle>(
    fetch,
    backendUrl('/CodeSystem', `_id=${params.id}&_elements=version,title,description`),
    { error: resourceError('CodeSystem', params.id) }
  );

  return {
    codeSystem: bundle.entry?.[0].resource as CodeSystem
  };
};

export const actions = {
  default: async ({ request, fetch, params }) => {
    const data = await request.formData();
    const code = data.get('code') as string;
    const display = data.get('display') as string;
    const displayLanguage = data.get('displayLanguage') as string;

    const parameters: ParametersParameter[] = [
      {
        name: 'code',
        valueCode: code
      }
    ];

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

    const res = await fetch(backendUrl(`/CodeSystem/${params.id}/$validate-code`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify({
        resourceType: 'Parameters',
        parameter: parameters
      })
    });

    const result = { code, display, displayLanguage };

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        ...result,
        incorrect: true,
        msg: error.issue?.[0]?.diagnostics ?? error.issue?.[0]?.details?.text
      });
    }

    return { ...result, result: (await res.json()) as Parameters };
  }
} satisfies Actions;
