import type { PageServerLoad } from './$types';
import type { Task } from 'fhir/r4';
import { error } from '@sveltejs/kit';

import { backendUrl } from '$lib/backend.js';
import { fetchFhir } from '$lib/fetch.js';
import { jobError } from '$lib/jobs.js';
import { toJob } from '$lib/jobs/compact';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const task = await fetchFhir<Task>(fetch, backendUrl(`/__admin/Task/${params.id}`), {
    error: jobError(params.id)
  });

  const job = toJob(task);
  if (job === undefined) {
    error(500, 'Problem while reading the Job');
  }

  return { job: job };
};
