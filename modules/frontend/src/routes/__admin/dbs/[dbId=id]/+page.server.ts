import type { Actions, PageServerLoad } from './$types';
import { resolve } from '$app/paths';
import { error, fail, type NumericRange, redirect } from '@sveltejs/kit';
import { url } from '$lib/canonical';
import {
  defaultParameters,
  latestResults,
  newTask,
  runningJob,
  toJob,
  type DiskPerfJob
} from '$lib/jobs/disk-perf';
import type { Bundle, OperationOutcome, Task } from 'fhir/r4';

async function loadDiskPerfJobs(fetch: typeof window.fetch): Promise<DiskPerfJob[]> {
  const query = `?code=${encodeURIComponent(url('CodeSystem/JobType') + '|disk-perf')}&_sort=-_lastUpdated&_count=100`;
  const res = await fetch(`/fhir/__admin/Task${query}`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: undefined,
      message: `An error happened while loading the disk performance measurement jobs. Please try again later.`
    });
  }

  const bundle = (await res.json()) as Bundle;
  return (
    bundle.entry
      ?.map((e) => toJob(e.resource as Task))
      .filter((j): j is DiskPerfJob => j !== undefined) || []
  );
}

export const load: PageServerLoad = async ({ fetch, params }) => {
  const jobs = await loadDiskPerfJobs(fetch);
  return {
    diskPerf: {
      results: latestResults(jobs, params.dbId),
      running: runningJob(jobs, params.dbId)
    }
  };
};

export const actions = {
  diskPerf: async ({ fetch, params }) => {
    const res = await fetch('/fhir/__admin/Task', {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
      body: JSON.stringify(newTask({ database: params.dbId, ...defaultParameters }))
    });

    if (!res.ok) {
      const error: OperationOutcome = await res.json();
      return fail(400, {
        msg: error.issue?.[0]?.diagnostics ?? error.issue?.[0]?.details?.text
      });
    }

    redirect(303, resolve('/__admin/dbs/[dbId=id]', params));
  }
} satisfies Actions;
