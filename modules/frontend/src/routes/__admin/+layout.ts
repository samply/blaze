import type { LayoutLoad } from './$types';

import { resolve } from '$app/paths';
import { error, type NumericRange } from '@sveltejs/kit';

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
  const res = await fetch(resolve('/__admin'), {
    headers: { Accept: 'application/json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, {
      short: undefined,
      message: `An error happened while loading the admin root. Please try again later.`
    });
  }

  return (await res.json()) as Data;
};
