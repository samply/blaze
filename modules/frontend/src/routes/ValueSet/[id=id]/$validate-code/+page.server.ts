import type { Actions } from './$types';
import type { OperationOutcome, Parameters, ParametersParameter } from 'fhir/r4';
import { resolve } from '$app/paths';
import { fail } from '@sveltejs/kit';

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

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        code,
        system,
        systemVersion,
        display,
        displayLanguage,
        inferSystem,
        incorrect: true,
        msg: error.issue[0]?.diagnostics ?? error.issue[0]?.details?.text
      });
    }

    return {
      code,
      system,
      systemVersion,
      display,
      displayLanguage,
      inferSystem,
      result: (await res.json()) as Parameters
    };
  }
} satisfies Actions;
