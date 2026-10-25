import type { PageServerLoad } from './$types';
import { error } from '@sveltejs/kit';

import type { Bundle, Task } from 'fhir/r4';
import { backendUrl } from '#lib/backend.js';
import { toJob } from '#lib/jobs/async-interaction.js';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(
    backendUrl('/__admin/Task', `_id=${params.id}&_include=Task:input&_include=Task:output`),
    {
      headers: { Accept: 'application/fhir+json' }
    }
  );

  if (!res.ok) {
    error(res.status, `Error while loading the job with ID ${params.id}.`);
  }

  const bundle = (await res.json()) as Bundle;
  const task = bundle.entry?.[0]?.resource as Task;
  if (task === undefined) {
    error(404, `The job with ID ${params.id} was not found.`);
  }

  const includes = bundle.entry?.filter((e) => e.search?.mode === 'include') || [];

  const job = toJob(task, includes);
  if (job === undefined) {
    error(500, 'Error while reading the Job');
  }

  return { job: job };
};
