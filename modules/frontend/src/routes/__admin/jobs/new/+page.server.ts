import type { Actions } from './$types';
import { base } from '$app/paths';
import type { OperationOutcome, Task } from 'fhir/r4';
import { fail, redirect } from '@sveltejs/kit';

const display = new Map();
display.set('re-index', '(Re)Index a Search Parameter');

export const actions = {
	default: async ({ request, fetch }) => {
		const data = await request.formData();
		const searchParamUrl = data.get('search-param-url');

		const res = await fetch(`${base}/__admin/Task`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
			body: JSON.stringify({
				resourceType: 'Task',
				meta: {
					profile: ['https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob']
				},
				intent: 'order',
				status: 'ready',
				code: {
					coding: [
						{
							code: data.get('type'),
							system: 'https://samply.github.io/blaze/fhir/CodeSystem/JobType',
							display: display.get(data.get('type'))
						}
					]
				},
				authoredOn: new Date().toISOString(),
				input: [
					{
						type: {
							coding: [
								{
									code: 'search-param-url',
									system: 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter'
								}
							]
						},
						valueCanonical: searchParamUrl
					}
				]
			})
		});

		if (!res.ok) {
			const error: OperationOutcome = await res.json();
			return fail(400, { searchParamUrl, incorrect: true, msg: error.issue[0]?.details?.text });
		}

		const task: Task = await res.json();
		redirect(303, `${base}/__admin/jobs/${task.id}`);
	}
} satisfies Actions;
