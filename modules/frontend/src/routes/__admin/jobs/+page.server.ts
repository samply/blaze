import type { Actions, PageServerLoad } from './$types';
import { resolve } from '$app/paths';
import { error, fail, type NumericRange, redirect } from '@sveltejs/kit';
import { pascalCase } from 'change-case';
import { type Job, toJob } from '$lib/jobs';
import {
  extractRequest,
  extractProcessingDuration as extractAsyncProcessingDuration
} from '$lib/jobs/async-interaction';
import {
  extractDatabase,
  extractColumnFamily,
  extractProcessingDuration as extractCompactProcessingDuration
} from '$lib/jobs/compact';
import {
  extractSearchParamUrl,
  extractProcessingDuration as extractReIndexProcessingDuration
} from '$lib/jobs/re-index';
import type { Bundle, BundleEntry, Task } from 'fhir/r4';

export interface SummaryJob extends Job {
  detail: string;
  processingDuration?: number;
}

function toSummaryJob(job: Task, includes: BundleEntry[]): SummaryJob | undefined {
  const baseJob = toJob(job);
  if (baseJob === undefined) {
    return undefined;
  }

  if (baseJob.type.code === 'async-interaction') {
    const request = extractRequest(job, includes);
    return request === undefined
      ? undefined
      : { ...baseJob, detail: request, processingDuration: extractAsyncProcessingDuration(job) };
  }

  if (baseJob.type.code === 'compact') {
    const database = extractDatabase(job);
    const columnFamily = extractColumnFamily(job);
    return database === undefined || columnFamily === undefined
      ? undefined
      : {
          ...baseJob,
          detail: pascalCase(database) + ' / ' + pascalCase(columnFamily),
          processingDuration: extractCompactProcessingDuration(job)
        };
  }

  if (baseJob.type.code === 're-index') {
    const searchParamUrl = extractSearchParamUrl(job);
    return searchParamUrl === undefined
      ? undefined
      : {
          ...baseJob,
          detail: searchParamUrl,
          processingDuration: extractReIndexProcessingDuration(job)
        };
  }

  return { ...baseJob, detail: 'TODO' };
}

async function loadJobs(fetch: typeof window.fetch, status?: string): Promise<SummaryJob[]> {
  const query = (status ? `?status=${status}&` : '?') + '_sort=-_lastUpdated&_include=Task:input';
  const res = await fetch(`/fhir/__admin/Task${query}`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: undefined,
      message: `An error happened while loading the list of running jobs. Please try again later.`
    });
  }

  const bundle = (await res.json()) as Bundle;
  const includes = bundle.entry?.filter((e) => e.search?.mode === 'include') || [];
  return (
    bundle.entry
      ?.filter((e) => e.search?.mode === 'match')
      .map((e) => toSummaryJob(e.resource as Task, includes))
      .filter((j): j is SummaryJob => j !== undefined) || []
  );
}

export const load: PageServerLoad = async ({ fetch }) => {
  return { all: await loadJobs(fetch) };
};

export const actions = {
  pause: async ({ request, fetch }) => {
    const data = await request.formData();
    const jobId = data.get('job-id');

    const res = await fetch(`/fhir/__admin/Task/${jobId}/$pause`, {
      method: 'POST',
      headers: { Accept: 'application/fhir+json' }
    });

    if (!res.ok) {
      return fail(400);
    }

    redirect(303, resolve('/__admin/jobs'));
  },
  resume: async ({ request, fetch }) => {
    const data = await request.formData();
    const jobId = data.get('job-id');

    const res = await fetch(`/fhir/__admin/Task/${jobId}/$resume`, {
      method: 'POST',
      headers: { Accept: 'application/fhir+json' }
    });

    if (!res.ok) {
      return fail(400);
    }

    redirect(303, resolve('/__admin/jobs'));
  },
  cancel: async ({ request, fetch }) => {
    const data = await request.formData();
    const jobId = data.get('job-id');

    const res = await fetch(`/fhir/__admin/Task/${jobId}/$cancel`, {
      method: 'POST',
      headers: { Accept: 'application/fhir+json' }
    });

    if (!res.ok) {
      return fail(400);
    }

    redirect(303, resolve('/__admin/jobs'));
  }
} satisfies Actions;
