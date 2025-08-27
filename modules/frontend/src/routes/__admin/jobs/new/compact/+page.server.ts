import type { Actions } from './$types';
import { resolve } from '$app/paths';
import type { OperationOutcome, Task } from 'fhir/r5';
import { fail, redirect } from '@sveltejs/kit';

export const actions = {
  default: async ({ request, fetch }) => {
    const data = await request.formData();
    const database = data.get('database') as string;
    const columnFamily = data.get('column-family') as string;

    const res = await fetch('/fhir/__admin/Task', {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify({
        resourceType: 'Task',
        meta: {
          profile: ['https://samply.github.io/blaze/fhir/StructureDefinition/CompactJob']
        },
        intent: 'order',
        status: 'ready',
        code: {
          coding: [
            {
              code: 'compact',
              system: 'https://samply.github.io/blaze/fhir/CodeSystem/JobType',
              display: 'Compact a Database Column Family'
            }
          ]
        },
        authoredOn: new Date().toISOString(),
        input: [
          {
            type: {
              coding: [
                {
                  code: 'database',
                  system: 'https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter'
                }
              ]
            },
            valueCode: database
          },
          {
            type: {
              coding: [
                {
                  code: 'column-family',
                  system: 'https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter'
                }
              ]
            },
            valueCode: columnFamily
          }
        ]
      })
    });

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        database,
        columnFamily,
        incorrect: true,
        msg: error.issue[0]?.diagnostics ?? error.issue[0]?.details?.text
      });
    }

    const task: Task = await res.json();
    redirect(
      303,
      task.id === undefined
        ? resolve('/__admin/jobs')
        : resolve('/__admin/jobs/compact/[id=id]', { id: task.id })
    );
  }
} satisfies Actions;
