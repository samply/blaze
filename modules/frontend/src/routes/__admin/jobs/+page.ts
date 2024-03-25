import type { PageLoad } from './$types';
import type { Bundle, Task } from 'fhir/r4';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

async function loadJobs(fetch: typeof window.fetch, status: string): Promise<Bundle<Task>> {
	const res = await fetch(`${base}/__admin/jobs?status=${status}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, {
			short: undefined,
			message: `An error happened while loading the list of running jobs. Please try again later.`
		});
	}

	return await res.json();
}

export const load: PageLoad = async ({ fetch }) => {
	return { ready: await loadJobs(fetch, 'ready'), running: await loadJobs(fetch, 'running') };
};
