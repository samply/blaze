import type { Actions } from './$types';
import type { OperationOutcome, ParametersParameter, ValueSet } from 'fhir/r5';
import { resolve } from '$app/paths';
import { fail } from '@sveltejs/kit';

export const actions = {
  default: async ({ request, fetch, params }) => {
    const data = await request.formData();
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

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        property,
        displayLanguage,
        systemVersion,
        includeDesignations,
        includeDefinition,
        activeOnly,
        excludeNested,
        incorrect: true,
        msg: error.issue[0]?.diagnostics ?? error.issue[0]?.details?.text
      });
    }

    return {
      property,
      displayLanguage,
      systemVersion,
      includeDesignations,
      includeDefinition,
      activeOnly,
      excludeNested,
      valueSet: (await res.json()) as ValueSet
    };
  }
} satisfies Actions;
