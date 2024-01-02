import { base } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

export interface Setting {
	name: string;
	value: string | number;
	defaultValue: string | number;
}

export interface Feature {
	name: string;
	toggle: string;
	enabled: boolean;
}

export interface Data {
	settings: Setting[];
	features: Feature[];
}

export async function load({ fetch }): Promise<Data> {
	const res = await fetch(`${base}/__admin`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		error(res.status as NumericRange<400, 599>, {
			short: undefined,
			message: `An error happened while loading the admin root. Please try again later.`
		});
	}

	return await res.json();
}
