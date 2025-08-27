import type { Actions } from './$types';
import type { OperationOutcome, Task } from 'fhir/r5';
import { resolve } from '$app/paths';
import { fail, redirect } from '@sveltejs/kit';

export const actions = {
  default: async ({ request, fetch }) => {
    const data = await request.formData();
    const searchParamUrl = data.get('search-param-url') as string;

    const res = await fetch('/fhir/__admin/Task', {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify({
        resourceType: 'Task',
        meta: {
          profile: ['https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob']
        },
        intent: 'order',
        status: 'ready',
        code: {
          coding: [
            {
              code: 're-index',
              system: 'https://samply.github.io/blaze/fhir/CodeSystem/JobType',
              display: '(Re)Index a Search Parameter'
            }
          ]
        },
        authoredOn: new Date().toISOString(),
        input: [
          {
            type: {
              coding: [
                {
                  code: 'search-param-url',
                  system: 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter'
                }
              ]
            },
            valueCanonical: searchParamUrl
          }
        ]
      })
    });

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        searchParamUrl,
        incorrect: true,
        msg: error.issue[0]?.diagnostics ?? error.issue[0]?.details?.text
      });
    }

    const task: Task = await res.json();
    redirect(
      303,
      task.id === undefined
        ? resolve('/__admin/jobs')
        : resolve('/__admin/jobs/re-index/[id=id]', { id: task.id })
    );
  }
} satisfies Actions;
