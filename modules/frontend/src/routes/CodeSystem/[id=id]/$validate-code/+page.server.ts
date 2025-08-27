import type { Actions } from './$types';
import type { OperationOutcome, Parameters, ParametersParameter } from 'fhir/r5';
import { resolve } from '$app/paths';
import { fail } from '@sveltejs/kit';

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

    const res = await fetch(resolve('/CodeSystem/[id=id]/$validate-code', params), {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify({
        resourceType: 'Parameters',
        parameter: parameters
      })
    });

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        code,
        display,
        displayLanguage,
        incorrect: true,
        msg: error.issue[0]?.diagnostics ?? error.issue[0]?.details?.text
      });
    }

    return { code, display, displayLanguage, result: (await res.json()) as Parameters };
  }
} satisfies Actions;
