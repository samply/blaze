import { base } from '$app/paths';
import { error } from '@sveltejs/kit';

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

export interface Features {
	[key: string]: Feature;
}

export interface Data {
	settings: Setting[];
	features: Features;
}

export async function load({ fetch }): Promise<Data> {
	const res = await fetch(`${base}/__admin`, {
		headers: { Accept: 'application/json' }
	});

	if (!res.ok) {
		throw error(res.status, {
			short: undefined,
			message: `An error happend while loading the admin root. Please try again later.`
		});
	}

	return await res.json();
}
