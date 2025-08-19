import { input, output, type Job, toJob as toBaseJob } from '$lib/jobs';
import type { Task } from 'fhir/r5';

export interface ReIndexJob extends Job {
  searchParamUrl: string;
  totalResources?: number;
  resourcesProcessed?: number;
  processingDuration?: number;
}

const parameterUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter';
const outputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobOutput';

export function extractSearchParamUrl(job: Task): string | undefined {
  return input(job, parameterUrl, 'search-param-url')?.valueCanonical;
}

export function extractProcessingDuration(job: Task): number | undefined {
  return output(job, outputUrl, 'processing-duration')?.valueQuantity?.value;
}

export function toJob(job: Task): ReIndexJob | undefined {
  const baseJob = toBaseJob(job);
  const searchParamUrl = extractSearchParamUrl(job);

  return baseJob === undefined || searchParamUrl === undefined
    ? undefined
    : {
        ...baseJob,
        searchParamUrl: searchParamUrl,
        totalResources: output(job, outputUrl, 'total-resources')?.valueUnsignedInt,
        resourcesProcessed: output(job, outputUrl, 'resources-processed')?.valueUnsignedInt,
        processingDuration: extractProcessingDuration(job)
      };
}
