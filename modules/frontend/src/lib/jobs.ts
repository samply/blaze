import { url, matches } from '$lib/canonical';
import type { CodeableConcept, Coding, Task, TaskInput, TaskOutput } from 'fhir/r4';

const numberUrl = url('sid/JobNumber');
const typeUrl = url('CodeSystem/JobType');
const statusReasonUrl = url('CodeSystem/JobStatusReason');
const outputUrl = url('CodeSystem/JobOutput');

// Like `coding` from `$lib/fhir`, but accepts both the current and the legacy
// form of a Blaze canonical so jobs stored with either system are read.
function jobCoding(concept: CodeableConcept, canonicalUrl: string): Coding | undefined {
  return concept.coding?.filter((c) => matches(canonicalUrl, c.system))[0];
}

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
  const numberIdentifier = job.identifier?.filter((c) => matches(numberUrl, c.system))[0];
  const value = numberIdentifier?.value;
  const num = value !== undefined ? parseInt(value) : undefined;
  return Number.isNaN(num) ? undefined : num;
}

export function statusReason(job: Task): string | undefined {
  const statusReasonCoding = job.statusReason
    ? jobCoding(job.statusReason, statusReasonUrl)
    : undefined;
  return statusReasonCoding?.code;
}

export function input(job: Task, system: string, code: string): TaskInput | undefined {
  return job.input?.filter((i) => jobCoding(i.type, system)?.code == code)[0];
}

export function output(job: Task, system: string, code: string): TaskOutput | undefined {
  return job.output?.filter((i) => jobCoding(i.type, system)?.code == code)[0];
}

export function toJob(job: Task): Job | undefined {
  const lastUpdated = job.meta?.lastUpdated;
  const n = number(job);
  const type = job.code ? jobCoding(job.code, typeUrl) : undefined;

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
