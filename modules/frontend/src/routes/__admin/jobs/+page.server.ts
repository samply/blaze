import type { Actions } from './$types';
import { base } from '$app/paths';
import { fail, redirect } from '@sveltejs/kit';

export const actions = {
	pause: async ({ request, fetch }) => {
		const data = await request.formData();
		const jobId = data.get('job-id');

		const res = await fetch(`${base}/__admin/Task/${jobId}/$pause`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' }
		});

		if (!res.ok) {
			return fail(400);
		}

		redirect(303, `${base}/__admin/jobs`);
	},
	resume: async ({ request, fetch }) => {
		const data = await request.formData();
		const jobId = data.get('job-id');

		const res = await fetch(`${base}/__admin/Task/${jobId}/$resume`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' }
		});

		if (!res.ok) {
			return fail(400);
		}

		redirect(303, `${base}/__admin/jobs`);
	}
} satisfies Actions;
