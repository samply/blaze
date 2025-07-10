import { coding } from '$lib/fhir';
import type { Task, TaskInput, TaskOutput } from 'fhir/r4';

const numberUrl = 'https://samply.github.io/blaze/fhir/sid/JobNumber';
const typeUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobType';
const statusReasonUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason';
const outputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobOutput';

export interface Job {
  id: string;
  lastUpdated: string;
  number: number;
  status: string;
  statusReason?: string;
  type: { code: string; display: string };
  authoredOn: string;
  error?: string;
}

export function number(job: Task): number | undefined {
  const numberIdentifier = job.identifier?.filter((c) => c.system == numberUrl)[0];
  const value = numberIdentifier?.value;
  const num = value !== undefined ? parseInt(value) : undefined;
  return Number.isNaN(num) ? undefined : num;
}

export function statusReason(job: Task): string | undefined {
  const statusReasonCoding = job.statusReason
    ? coding(job.statusReason, statusReasonUrl)
    : undefined;
  return statusReasonCoding?.code;
}

export function input(job: Task, system: string, code: string): TaskInput | undefined {
  return job.input?.filter((i) => coding(i.type, system)?.code == code)[0];
}

export function output(job: Task, system: string, code: string): TaskOutput | undefined {
  return job.output?.filter((i) => coding(i.type, system)?.code == code)[0];
}

export function toJob(job: Task): Job | undefined {
  const lastUpdated = job.meta?.lastUpdated;
  const n = number(job);
  const type = job.code ? coding(job.code, typeUrl) : undefined;

  return job.id === undefined ||
    lastUpdated === undefined ||
    n === undefined ||
    type?.code === undefined ||
    job.authoredOn === undefined
    ? undefined
    : {
        id: job.id,
        lastUpdated: lastUpdated,
        number: n,
        status: job.status,
        statusReason: statusReason(job),
        type: { code: type?.code, display: type?.display || type?.code },
        authoredOn: job.authoredOn,
        error: output(job, outputUrl, 'error')?.valueString
      };
}
