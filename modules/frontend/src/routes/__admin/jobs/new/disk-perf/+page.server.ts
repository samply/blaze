import type { Actions } from './$types';
import { resolve } from '$app/paths';
import { newTask } from '$lib/jobs/disk-perf';
import type { OperationOutcome, Task } from 'fhir/r4';
import { fail, redirect } from '@sveltejs/kit';

export const actions = {
  default: async ({ request, fetch }) => {
    const data = await request.formData();
    const database = data.get('database') as string;
    const fileSize = parseFloat(data.get('file-size') as string);
    const phaseDuration = parseFloat(data.get('phase-duration') as string);
    const maxConcurrency = parseInt(data.get('max-concurrency') as string);

    const res = await fetch('/fhir/__admin/Task', {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify(newTask({ database, fileSize, phaseDuration, maxConcurrency }))
    });

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        database,
        fileSize,
        phaseDuration,
        maxConcurrency,
        incorrect: true,
        msg: error.issue?.[0]?.diagnostics ?? error.issue?.[0]?.details?.text
      });
    }

    const task: Task = await res.json();
    redirect(
      303,
      task.id === undefined
        ? resolve('/__admin/jobs')
        : resolve('/__admin/jobs/disk-perf/[id=id]', { id: task.id })
    );
  }
} satisfies Actions;
