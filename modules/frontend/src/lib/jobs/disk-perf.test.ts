import type { Task } from 'fhir/r4';

import { describe, expect, it } from 'vitest';
import { base } from '../canonical.js';
import {
  bestReadIops,
  defaultParameters,
  extractDatabase,
  extractScore,
  latestResults,
  newTask,
  runningJob,
  toJob,
  type DiskPerfJob
} from './disk-perf.js';

// A minimal disk-perf job as stored by Blaze. The disk-perf job type never
// existed in the legacy IG edition, so it only carries current canonicals.
function diskPerfJob(): Task {
  return {
    resourceType: 'Task',
    id: 'AAAAAAAAAAAAAAAA',
    meta: { lastUpdated: '2024-04-13T10:05:20.927Z' },
    identifier: [{ system: `${base}/sid/JobNumber`, value: '1' }],
    status: 'ready',
    intent: 'order',
    code: {
      coding: [
        {
          system: `${base}/CodeSystem/JobType`,
          code: 'disk-perf',
          display: 'Measure Disk Performance'
        }
      ]
    },
    authoredOn: '2024-04-13T10:05:20.927Z'
  };
}

function input(code: string, value: object): object {
  return {
    type: { coding: [{ system: `${base}/CodeSystem/DiskPerfJobParameter`, code: code }] },
    ...value
  };
}

function output(code: string, value: object): object {
  return {
    type: { coding: [{ system: `${base}/CodeSystem/DiskPerfJobOutput`, code: code }] },
    ...value
  };
}

function quantity(value: number, code: string): object {
  return {
    valueQuantity: { value: value, unit: code, system: 'http://unitsofmeasure.org', code: code }
  };
}

/** an output of one run of the random read sweep, tagged with its concurrency */
function readOutput(code: string, concurrency: number, value: number, unit: string): object {
  return {
    extension: [
      { url: `${base}/StructureDefinition/disk-perf-concurrency`, valuePositiveInt: concurrency }
    ],
    ...output(code, quantity(value, unit))
  };
}

describe('extractDatabase', () => {
  it('returns the database input', () => {
    const job = diskPerfJob();
    job.input = [input('database', { valueCode: 'transaction' })] as Task['input'];
    expect(extractDatabase(job)).toBe('transaction');
  });

  it('defaults to index', () => {
    expect(extractDatabase(diskPerfJob())).toBe('index');
  });
});

describe('toJob', () => {
  it('reads a ready job with all parameters', () => {
    const job = diskPerfJob();
    job.input = [
      input('database', { valueCode: 'index' }),
      input('file-size', quantity(4, 'GiBy')),
      input('phase-duration', quantity(30, 's')),
      input('max-concurrency', { valuePositiveInt: 32 })
    ] as Task['input'];

    expect(toJob(job)).toMatchObject({
      id: 'AAAAAAAAAAAAAAAA',
      number: 1,
      status: 'ready',
      type: { code: 'disk-perf', display: 'Measure Disk Performance' },
      database: 'index',
      fileSize: 4,
      phaseDuration: 30,
      maxConcurrency: 32
    });
  });

  it('reads the phase outputs of a job in progress', () => {
    const job = diskPerfJob();
    job.status = 'in-progress';
    job.output = [
      output('current-phase', { valueCode: 'rand-read' }),
      output('phase-progress', { valueUnsignedInt: 40 })
    ] as Task['output'];

    expect(toJob(job)).toMatchObject({
      status: 'in-progress',
      currentPhase: 'rand-read',
      phaseProgress: 40
    });
  });

  it('reads the results of a completed job', () => {
    const job = diskPerfJob();
    job.status = 'completed';
    job.output = [
      output('seq-write-throughput', quantity(176918923, 'By/s')),
      // the runs of the random read sweep are interleaved by metric to show
      // that grouping happens by the concurrency extension, not by order
      readOutput('read-iops', 1, 14100.5, '/s'),
      readOutput('read-iops', 2, 27801.3, '/s'),
      readOutput('read-throughput', 1, 231030784, 'By/s'),
      readOutput('read-throughput', 2, 455495680, 'By/s'),
      readOutput('read-latency-p50', 1, 68.2, 'us'),
      readOutput('read-latency-p50', 2, 70.1, 'us'),
      readOutput('read-latency-p95', 1, 110.2, 'us'),
      readOutput('read-latency-p95', 2, 121.9, 'us'),
      readOutput('read-latency-p99', 1, 170.0, 'us'),
      readOutput('read-latency-p99', 2, 180.5, 'us'),
      readOutput('read-latency-max', 1, 3527.0, 'us'),
      readOutput('read-latency-max', 2, 4100.0, 'us'),
      output('fsync-rate', quantity(446.3, '/s')),
      output('fsync-latency-p50', quantity(1923.1, 'us')),
      output('fsync-latency-p95', quantity(3234.3, 'us')),
      output('fsync-latency-p99', quantity(5931.6, 'us')),
      output('direct-io', { valueBoolean: true }),
      output('score', { valueDecimal: 53.0 }),
      output('rating', { valueCode: 'good' }),
      output('processing-duration', quantity(6.8, 's'))
    ] as Task['output'];

    expect(extractScore(job)).toBe(53.0);
    expect(toJob(job)).toMatchObject({
      status: 'completed',
      seqWriteThroughput: 176918923,
      readRuns: [
        {
          concurrency: 1,
          iops: 14100.5,
          throughput: 231030784,
          latencyP50: 68.2,
          latencyP95: 110.2,
          latencyP99: 170.0,
          latencyMax: 3527.0
        },
        {
          concurrency: 2,
          iops: 27801.3,
          throughput: 455495680,
          latencyP50: 70.1,
          latencyP95: 121.9,
          latencyP99: 180.5,
          latencyMax: 4100.0
        }
      ],
      fsyncRate: 446.3,
      fsyncLatencyP50: 1923.1,
      fsyncLatencyP95: 3234.3,
      fsyncLatencyP99: 5931.6,
      directIo: true,
      score: 53.0,
      rating: 'good',
      processingDuration: 6.8
    });
  });

  it('sorts the read runs by ascending concurrency', () => {
    const job = diskPerfJob();
    job.status = 'completed';
    job.output = [
      readOutput('read-iops', 8, 51860.9, '/s'),
      readOutput('read-iops', 1, 14100.5, '/s')
    ] as Task['output'];

    expect(toJob(job)?.readRuns?.map((r) => r.concurrency)).toEqual([1, 8]);
  });

  it('has no read runs without read outputs', () => {
    expect(toJob(diskPerfJob())?.readRuns).toBeUndefined();
  });
});

describe('bestReadIops', () => {
  it('returns the maximum IOPS over all read runs', () => {
    const job = diskPerfJob();
    job.output = [
      readOutput('read-iops', 1, 14100.5, '/s'),
      readOutput('read-iops', 8, 51860.9, '/s'),
      readOutput('read-iops', 32, 48000.1, '/s')
    ] as Task['output'];

    expect(bestReadIops(toJob(job) as DiskPerfJob)).toBe(51860.9);
  });

  it('returns undefined without read runs', () => {
    expect(bestReadIops(toJob(diskPerfJob()) as DiskPerfJob)).toBeUndefined();
  });
});

function summaryJob(id: string, database: string, status: string, score?: number): DiskPerfJob {
  return {
    id: id,
    lastUpdated: '2024-04-13T10:05:20.927Z',
    number: 1,
    status: status,
    type: { code: 'disk-perf', display: 'Measure Disk Performance' },
    authoredOn: '2024-04-13T10:05:20.927Z',
    database: database,
    score: score
  };
}

describe('latestResults', () => {
  it('returns the most recent job with results of the given database', () => {
    const jobs = [
      summaryJob('id-0', 'transaction', 'completed', 40),
      summaryJob('id-1', 'index', 'failed'),
      summaryJob('id-2', 'index', 'completed', 53),
      summaryJob('id-3', 'index', 'completed', 60)
    ];
    expect(latestResults(jobs, 'index')?.id).toBe('id-2');
  });

  it('returns undefined if no job of the given database has results', () => {
    const jobs = [
      summaryJob('id-0', 'transaction', 'completed', 40),
      summaryJob('id-1', 'index', 'failed')
    ];
    expect(latestResults(jobs, 'index')).toBeUndefined();
  });
});

describe('runningJob', () => {
  it('returns the most recent unfinished job of the given database', () => {
    const jobs = [
      summaryJob('id-0', 'transaction', 'in-progress'),
      summaryJob('id-1', 'index', 'ready'),
      summaryJob('id-2', 'index', 'completed', 53)
    ];
    expect(runningJob(jobs, 'index')?.id).toBe('id-1');
  });

  it.each(['ready', 'in-progress', 'on-hold'])('counts a %s job as running', (status) => {
    expect(runningJob([summaryJob('id-0', 'index', status)], 'index')?.id).toBe('id-0');
  });

  it.each(['completed', 'failed', 'cancelled'])('ignores a %s job', (status) => {
    expect(runningJob([summaryJob('id-0', 'index', status)], 'index')).toBeUndefined();
  });
});

describe('newTask', () => {
  it('creates a task with the given database and the default parameters', () => {
    const task = newTask({ database: 'transaction', ...defaultParameters });

    expect(extractDatabase(task)).toBe('transaction');
    expect(task.authoredOn).toBeDefined();
    expect(task).toMatchObject({
      resourceType: 'Task',
      meta: { profile: [`${base}/StructureDefinition/DiskPerfJob`] },
      intent: 'order',
      status: 'ready',
      code: {
        coding: [
          {
            system: `${base}/CodeSystem/JobType`,
            code: 'disk-perf',
            display: 'Measure Disk Performance'
          }
        ]
      },
      input: [
        input('database', { valueCode: 'transaction' }),
        input('file-size', quantity(4, 'GiBy')),
        input('phase-duration', quantity(30, 's')),
        input('max-concurrency', { valuePositiveInt: 32 })
      ]
    });
  });
});
