import { coding } from '$lib/fhir';
import type { Task, TaskInput, TaskOutput } from 'fhir/r4';

const numberUrl = 'https://samply.github.io/blaze/fhir/sid/JobNumber';
const typeUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobType';
const statusReasonUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason';

export function number(job: Task): string {
	const numberIdentifier = job.identifier?.filter((c) => c.system == numberUrl)[0];
	return numberIdentifier?.value || 'unknown';
}

export function type(job: Task): string {
	const typeCoding = job.code ? coding(job.code, typeUrl) : undefined;
	return typeCoding?.display || typeCoding?.code || 'Unknown';
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
