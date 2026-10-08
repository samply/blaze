import type { PageServerLoad } from './$types';
import { error } from '@sveltejs/kit';

import type { Bundle, Task } from 'fhir/r4';
import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import { toJob } from '$lib/jobs/async-interaction';

export const load: PageServerLoad = async ({ fetch, params }) => {
  // A search never answers 404 for a missing job, so unlike the other job
  // pages this one reports a missing job from the empty bundle below.
  const bundle = await fetchFhir<Bundle>(
    fetch,
    backendUrl('/__admin/Task', `_id=${params.id}&_include=Task:input&_include=Task:output`),
    { error: `Error while loading the job with ID ${params.id}.` }
  );

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
