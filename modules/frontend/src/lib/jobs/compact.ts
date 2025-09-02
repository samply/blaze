import { input, output, type Job, toJob as toBaseJob } from '$lib/jobs';
import type { Task } from 'fhir/r5';

export interface CompactJob extends Job {
  database: string;
  columnFamily: string;
  processingDuration?: number;
}

const parameterUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter';
const outputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/CompactJobOutput';

export function extractDatabase(job: Task): string | undefined {
  return input(job, parameterUrl, 'database')?.valueCode;
}

export function extractColumnFamily(job: Task): string | undefined {
  return input(job, parameterUrl, 'column-family')?.valueCode;
}

export function extractProcessingDuration(job: Task): number | undefined {
  return output(job, outputUrl, 'processing-duration')?.valueQuantity?.value;
}

export function toJob(job: Task): CompactJob | undefined {
  const baseJob = toBaseJob(job);
  const database = extractDatabase(job);
  const columnFamily = extractColumnFamily(job);

  return baseJob === undefined || database === undefined || columnFamily === undefined
    ? undefined
    : {
        ...baseJob,
        database: database,
        columnFamily: columnFamily,
        processingDuration: extractProcessingDuration(job)
      };
}
