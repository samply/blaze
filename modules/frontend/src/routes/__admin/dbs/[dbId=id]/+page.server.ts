import type { Actions, PageServerLoad, RouteParams } from './$types';
import { resolve } from '$app/paths';
import { error, fail, type NumericRange, redirect } from '@sveltejs/kit';
import { backendUrl } from '$lib/backend.js';
import { url } from '$lib/canonical';
import { toTitleCase } from '$lib/util.js';
import {
  defaultParameters,
  latestResults,
  newTask,
  runningJob,
  toJob,
  type DiskPerfJob
} from '$lib/jobs/disk-perf';
import type { Bundle, OperationOutcome, Task } from 'fhir/r4';

type Fetch = typeof window.fetch;

export interface BlockCacheStats {
  capacity: number;
  usage: number;
}

export interface CompactionStats {
  pending: number;
  running: number;
}

export interface Stats {
  name: string;
  estimateLiveDataSize: number;
  usableSpace: number;
  blockCache?: BlockCacheStats;
  compactions: CompactionStats;
}

export interface ColumnFamilyData {
  name: string;
  estimateNumKeys: number;
  liveSstFilesSize: number;
  sizeAllMemTables: number;
}

async function loadStats(fetch: Fetch, params: RouteParams): Promise<Stats> {
  const res = await fetch(backendUrl(`/__admin/dbs/${params.dbId}/stats`), {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : undefined,
      message:
        res.status == 404
          ? `The ${toTitleCase(params.dbId)} database stats were not found.`
          : `An error happened while loading the ${toTitleCase(
              params.dbId
            )} database stats. Please try again later.`
    });
  }

  return await res.json();
}

async function loadColumnFamilies(fetch: Fetch, params: RouteParams): Promise<ColumnFamilyData[]> {
  const res = await fetch(backendUrl(`/__admin/dbs/${params.dbId}/column-families`), {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: res.status == 404 ? 'Not Found' : undefined,
      message:
        res.status == 404
          ? `The ${toTitleCase(params.dbId)} database column families were not found.`
          : `An error happened while loading the ${toTitleCase(
              params.dbId
            )} database column families. Please try again later.`
    });
  }

  return await res.json();
}

async function loadDiskPerfJobs(fetch: typeof window.fetch): Promise<DiskPerfJob[]> {
  const query = `code=${encodeURIComponent(url('CodeSystem/JobType') + '|disk-perf')}&_sort=-_lastUpdated&_count=100`;
  const res = await fetch(backendUrl('/__admin/Task', query), {
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
  // The three requests are independent, so they go out together.
  const [jobs, stats, columnFamilies] = await Promise.all([
    loadDiskPerfJobs(fetch),
    loadStats(fetch, params),
    loadColumnFamilies(fetch, params)
  ]);

  return {
    diskPerf: {
      results: latestResults(jobs, params.dbId),
      running: runningJob(jobs, params.dbId)
    },
    stats,
    columnFamilies
  };
};

export const actions = {
  diskPerf: async ({ fetch, params }) => {
    const res = await fetch(backendUrl('/__admin/Task'), {
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
