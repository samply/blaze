import type { LayoutLoad } from './$types';

import { resolve } from '$app/paths';
import { fetchJson } from '$lib/fetch.js';

export interface Setting {
  name: string;
  value?: string | number;
  masked?: boolean;
  defaultValue?: string | number;
}

export interface Feature {
  key: string;
  name: string;
  toggle: string;
  enabled: boolean;
}

export interface Data {
  settings: Setting[];
  features: Feature[];
}

export const load: LayoutLoad = async ({ fetch }) => {
  return fetchJson<Data>(
    fetch,
    resolve('/__admin'),
    'An error happened while loading the admin root. Please try again later.',
    { Accept: 'application/json' }
  );
};
