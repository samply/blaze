import type { Actions } from './$types';
import type { OperationOutcome, Parameters, ParametersParameter } from 'fhir/r4';
import { base } from '$app/paths';
import { fail } from '@sveltejs/kit';

export const actions = {
  default: async ({ request, fetch }) => {
    const data = await request.formData();
    const url = data.get('url') as string;
    const version = data.get('version') as string;
    const code = data.get('code') as string;
    const display = data.get('display') as string;
    const displayLanguage = data.get('displayLanguage') as string;

    const parameters: ParametersParameter[] = [
      {
        name: 'url',
        valueUri: url
      },
      {
        name: 'code',
        valueCode: code
      }
    ];

    if (version !== '') {
      parameters.push({
        name: 'version',
        valueString: version
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

    const res = await fetch(`${base}/CodeSystem/$validate-code`, {
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
        url,
        version,
        code,
        display,
        displayLanguage,
        incorrect: true,
        msg: error.issue[0]?.diagnostics ?? error.issue[0]?.details?.text
      });
    }

    return {
      url,
      version,
      code,
      display,
      displayLanguage,
      result: (await res.json()) as Parameters
    };
  }
} satisfies Actions;
