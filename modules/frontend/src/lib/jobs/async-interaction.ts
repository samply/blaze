import { input, type Job, output, toJob as toBaseJob } from '$lib/jobs';
import type { Bundle, BundleEntry, Task, TaskInput, TaskOutput } from 'fhir/r5';

export interface AsyncInteractionJob extends Job {
  request: string;
  t: number;
  responseStatus?: string;
  processingDuration?: number;
}

const parameterUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter';
const outputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobOutput';

function extractBundle(
  includes: BundleEntry[],
  inputOutput: TaskInput | TaskOutput | undefined
): Bundle | undefined {
  const requestBundleId = inputOutput?.valueReference?.reference?.split('/')?.[1];
  if (requestBundleId === undefined) {
    return undefined;
  }

  return includes.filter((e) => e.resource?.id === requestBundleId)?.[0]?.resource as Bundle;
}

export function extractRequest(job: Task, includes: BundleEntry[]): string | undefined {
  const requestBundle = extractBundle(includes, input(job, parameterUrl, 'bundle'));
  const method = requestBundle?.entry?.[0]?.request?.method;
  const url = requestBundle?.entry?.[0]?.request?.url;

  return method === undefined || url === undefined ? undefined : method + ' [base]/' + url;
}

export function extractProcessingDuration(job: Task): number | undefined {
  return output(job, outputUrl, 'processing-duration')?.valueQuantity?.value;
}

export function toJob(job: Task, includes: BundleEntry[]): AsyncInteractionJob | undefined {
  const baseJob = toBaseJob(job);
  const request = extractRequest(job, includes);
  const t = input(job, parameterUrl, 't')?.valueUnsignedInt;
  const responseBundle = extractBundle(includes, output(job, outputUrl, 'bundle'));
  const responseStatus = responseBundle?.entry?.[0]?.response?.status;

  return baseJob === undefined || request === undefined || t === undefined
    ? undefined
    : {
        ...baseJob,
        request: request,
        t: t,
        responseStatus: responseStatus,
        processingDuration: extractProcessingDuration(job)
      };
}
