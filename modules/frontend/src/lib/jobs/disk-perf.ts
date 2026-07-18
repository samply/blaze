import { url } from '$lib/canonical';
import { input, output, outputs, type Job, toJob as toBaseJob } from '$lib/jobs';
import type { Task, TaskOutput } from 'fhir/r4';

/** one run of the random read sweep */
export interface RandReadRun {
  /** number of concurrent reader threads of this run */
  concurrency: number;
  iops?: number;
  /** bytes per second */
  throughput?: number;
  /** microseconds */
  latencyP50?: number;
  latencyP95?: number;
  latencyP99?: number;
  latencyMax?: number;
}

export interface DiskPerfJob extends Job {
  database: string;
  /** test file size in GiB */
  fileSize?: number;
  /** duration of each run of the random read sweep and the fsync phase in seconds */
  phaseDuration?: number;
  /** maximum number of concurrent reader threads of the random read sweep */
  maxConcurrency?: number;
  currentPhase?: string;
  /** progress of the current phase in percent */
  phaseProgress?: number;
  /** bytes per second */
  seqWriteThroughput?: number;
  /** runs of the random read sweep, ordered by ascending concurrency */
  readRuns?: RandReadRun[];
  fsyncRate?: number;
  /** microseconds */
  fsyncLatencyP50?: number;
  fsyncLatencyP95?: number;
  fsyncLatencyP99?: number;
  directIo?: boolean;
  /** overall score between 0 and 100 */
  score?: number;
  rating?: string;
  /** seconds */
  processingDuration?: number;
}

const parameterUrl = url('CodeSystem/DiskPerfJobParameter');
const outputUrl = url('CodeSystem/DiskPerfJobOutput');
const concurrencyExtensionUrl = url('StructureDefinition/disk-perf-concurrency');

export function extractDatabase(job: Task): string {
  return input(job, parameterUrl, 'database')?.valueCode ?? 'index';
}

export function extractScore(job: Task): number | undefined {
  return output(job, outputUrl, 'score')?.valueDecimal;
}

export function extractProcessingDuration(job: Task): number | undefined {
  return output(job, outputUrl, 'processing-duration')?.valueQuantity?.value;
}

function outputQuantity(job: Task, code: string): number | undefined {
  return output(job, outputUrl, code)?.valueQuantity?.value;
}

function outputConcurrency(output: TaskOutput): number | undefined {
  return output.extension?.find((e) => e.url === concurrencyExtensionUrl)?.valuePositiveInt;
}

const runMetrics: [Exclude<keyof RandReadRun, 'concurrency'>, string][] = [
  ['iops', 'read-iops'],
  ['throughput', 'read-throughput'],
  ['latencyP50', 'read-latency-p50'],
  ['latencyP95', 'read-latency-p95'],
  ['latencyP99', 'read-latency-p99'],
  ['latencyMax', 'read-latency-max']
];

/**
 * Returns the runs of the random read sweep of `job`, grouping the repeated
 * read outputs by their concurrency extension, ordered by ascending
 * concurrency.
 */
export function extractReadRuns(job: Task): RandReadRun[] {
  const runs = new Map<number, RandReadRun>();
  for (const [metric, code] of runMetrics) {
    for (const output of outputs(job, outputUrl, code)) {
      const concurrency = outputConcurrency(output);
      const value = output.valueQuantity?.value;
      if (concurrency !== undefined && value !== undefined) {
        const run = runs.get(concurrency) ?? { concurrency };
        run[metric] = value;
        runs.set(concurrency, run);
      }
    }
  }
  return [...runs.values()].sort((a, b) => a.concurrency - b.concurrency);
}

/** Returns the maximum IOPS over all read runs of `job`, if any. */
export function bestReadIops(job: DiskPerfJob): number | undefined {
  const iops = job.readRuns?.flatMap((run) => (run.iops === undefined ? [] : [run.iops]));
  return iops?.length ? Math.max(...iops) : undefined;
}

/** The statuses of a job that was started but hasn't finished yet. */
const runningStatuses = ['ready', 'in-progress', 'on-hold'];

/**
 * Returns the most recent job of `database` that was started but hasn't
 * finished yet, if any. `jobs` must be sorted by descending last update.
 */
export function runningJob(jobs: DiskPerfJob[], database: string): DiskPerfJob | undefined {
  return jobs.find((job) => job.database === database && runningStatuses.includes(job.status));
}

/**
 * Returns the most recent job of `database` that has measurement results, if
 * any. `jobs` must be sorted by descending last update.
 */
export function latestResults(jobs: DiskPerfJob[], database: string): DiskPerfJob | undefined {
  return jobs.find((job) => job.database === database && job.score !== undefined);
}

/** Parameters for creating a new disk performance measurement job. */
export interface DiskPerfJobParameters {
  database: string;
  /** test file size in GiB */
  fileSize: number;
  /** duration of each run of the random read sweep and the fsync phase in seconds */
  phaseDuration: number;
  /** maximum number of concurrent reader threads of the random read sweep */
  maxConcurrency: number;
}

export const defaultParameters: Omit<DiskPerfJobParameters, 'database'> = {
  fileSize: 4,
  phaseDuration: 30,
  maxConcurrency: 32
};

function inputQuantity(code: string, value: number, unit: string): object {
  return {
    type: { coding: [{ code: code, system: parameterUrl }] },
    valueQuantity: { value: value, unit: unit, system: 'http://unitsofmeasure.org', code: unit }
  };
}

/** Builds the Task resource creating a new disk performance measurement job. */
export function newTask({
  database,
  fileSize,
  phaseDuration,
  maxConcurrency
}: DiskPerfJobParameters): Task {
  return {
    resourceType: 'Task',
    meta: { profile: [url('StructureDefinition/DiskPerfJob')] },
    intent: 'order',
    status: 'ready',
    code: {
      coding: [
        {
          code: 'disk-perf',
          system: url('CodeSystem/JobType'),
          display: 'Measure Disk Performance'
        }
      ]
    },
    authoredOn: new Date().toISOString(),
    input: [
      {
        type: { coding: [{ code: 'database', system: parameterUrl }] },
        valueCode: database
      },
      inputQuantity('file-size', fileSize, 'GiBy'),
      inputQuantity('phase-duration', phaseDuration, 's'),
      {
        type: { coding: [{ code: 'max-concurrency', system: parameterUrl }] },
        valuePositiveInt: maxConcurrency
      }
    ] as Task['input']
  };
}

export function toJob(job: Task): DiskPerfJob | undefined {
  const baseJob = toBaseJob(job);
  const readRuns = extractReadRuns(job);

  return baseJob === undefined
    ? undefined
    : {
        ...baseJob,
        database: extractDatabase(job),
        fileSize: input(job, parameterUrl, 'file-size')?.valueQuantity?.value,
        phaseDuration: input(job, parameterUrl, 'phase-duration')?.valueQuantity?.value,
        maxConcurrency: input(job, parameterUrl, 'max-concurrency')?.valuePositiveInt,
        currentPhase: output(job, outputUrl, 'current-phase')?.valueCode,
        phaseProgress: output(job, outputUrl, 'phase-progress')?.valueUnsignedInt,
        seqWriteThroughput: outputQuantity(job, 'seq-write-throughput'),
        readRuns: readRuns.length ? readRuns : undefined,
        fsyncRate: outputQuantity(job, 'fsync-rate'),
        fsyncLatencyP50: outputQuantity(job, 'fsync-latency-p50'),
        fsyncLatencyP95: outputQuantity(job, 'fsync-latency-p95'),
        fsyncLatencyP99: outputQuantity(job, 'fsync-latency-p99'),
        directIo: output(job, outputUrl, 'direct-io')?.valueBoolean,
        score: extractScore(job),
        rating: output(job, outputUrl, 'rating')?.valueCode,
        processingDuration: extractProcessingDuration(job)
      };
}
