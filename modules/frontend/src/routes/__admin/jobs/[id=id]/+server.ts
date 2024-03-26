import type { RequestHandler } from './$types';
import { base } from '$app/paths';

export const GET: RequestHandler = async ({ params, fetch }) => {
	const res = await fetch(`${base}/__admin/jobs/${params.id}`, {
		headers: { Accept: 'application/json' }
	});
	return new Response(await res.blob(), res);
};
