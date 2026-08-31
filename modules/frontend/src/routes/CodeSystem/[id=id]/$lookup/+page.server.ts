import type { Actions, PageServerLoad } from './$types';
import type {
  Bundle,
  CodeSystem,
  OperationOutcome,
  Parameters,
  ParametersParameter
} from 'fhir/r4';
import { base, resolve } from '$app/paths';
import { error, fail, type NumericRange } from '@sveltejs/kit';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(
    `${base}/CodeSystem?_id=${params.id}&_elements=version,title,description`,
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
          ? `The CodeSystem with ID ${params.id} was not found.`
          : res.status == 410
            ? `The CodeSystem with ID ${params.id} was deleted. Please look into the history.`
            : `An error happened while loading the CodeSystem with ID ${params.id}. Please try again later.`
    });
  }

  const bundle: Bundle = await res.json();

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

    const res = await fetch(resolve('/CodeSystem/[id=id]/$lookup', params), {
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
