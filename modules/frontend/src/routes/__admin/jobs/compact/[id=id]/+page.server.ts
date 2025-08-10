import type { PageServerLoad } from './$types';
import { error, type NumericRange } from '@sveltejs/kit';

import { toJob } from '$lib/jobs/compact';

export const load: PageServerLoad = async ({ fetch, params }) => {
  const res = await fetch(`/fhir/__admin/Task/${params.id}`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : res.status == 410 ? 'Gone' : undefined,
      message:
        res.status == 404
          ? `The job with ID ${params.id} was not found.`
          : res.status == 410
            ? `The job with ID ${params.id} was deleted. Please look into the history.`
            : `An error happened while loading the job with ID ${params.id}. Please try again later.`
    });
  }

  const job = toJob(await res.json());
  if (job === undefined) {
    error(500, 'Problem while reading the Job');
  }

  return { job: job };
};
