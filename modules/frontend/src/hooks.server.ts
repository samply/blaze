import { type Handle, type HandleFetch, redirect } from '@sveltejs/kit';
import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { sequence } from '@sveltejs/kit/hooks';

import { handle as handleAuthentication } from '$lib/server/auth';
import { base } from '$app/paths';

export const handleAuthorization: Handle = async ({ event, resolve }) => {
	if (event.route.id != '/__sign-in') {
		// authenticate the user
		const session = await event.locals.auth();

		if (!session) {
			throw redirect(307, `${base}/__sign-in?redirect=${event.url}`);
		}

		event.locals.session = session;
	}

	return resolve(event);
};

export const handle = sequence(handleAuthentication, handleAuthorization);

function isApiRoute(url: string) {
	return (
		url.endsWith('__search-params') ||
		url.endsWith('__search-includes') ||
		url.endsWith('__search-rev-includes')
	);
}

const origin = env.ORIGIN || '';
const forwarded = `host=${new URL(origin).host};proto=${new URL(origin).protocol.replace(':', '')}`;

export const handleFetch: HandleFetch = async ({ request, fetch, event }) => {
	if (isApiRoute(request.url)) {
		return fetch(request);
	}

	const session = event.locals.session;

	if (!session?.accessToken) {
		console.log('no session -> 401');
		error(401, {
			short: undefined,
			message: `Unauthorized.`
		});
	}

	request = new Request(request.url.replace(origin, env.BACKEND_BASE_URL || ''), {
		...request,
		credentials: 'omit',
		headers: {
			Accept: request.headers.get('accept') || 'application/fhir+json',
			Authorization: 'Bearer ' + session?.accessToken,
			Forwarded: forwarded
		}
	});

	return fetch(request);
};
