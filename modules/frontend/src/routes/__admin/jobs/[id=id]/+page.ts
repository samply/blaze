import type { PageLoad } from './$types';
import type { Task } from 'fhir/r4';

import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

export const load: PageLoad = async ({ fetch, params }) => {
	const res = await fetch(`${base}/__admin/jobs/${params.id}`, {
		headers: { Accept: 'application/fhir+json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, {
			short: res.status == 404 ? 'Not Found' : res.status == 410 ? 'Gone' : undefined,
			message:
				res.status == 404
					? `The job with ID ${params.id} was not found.`
					: res.status == 410
						? `The job with ID ${params.id} was deleted. Please look into the history.`
						: `An error happened while loading the job with ID ${params.id}. Please try again later.`
		});
	}

	return { job: (await res.json()) as Task };
};
