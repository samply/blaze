import type { Parameters } from 'fhir/r4';
import { backendUrl } from '#lib/backend.js';
import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { patientTotal, resourceTypeTotals } from '#lib/home/resource-type-totals.js';
import { fetchDemographics } from '#lib/home/fetch-demographics.js';

export const load: PageServerLoad = async ({ fetch }) => {
  const res = await fetch(backendUrl('/$totals'), { headers: { Accept: 'application/fhir+json' } });

  if (!res.ok) {
    error(res.status, 'error while executing the $totals operation');
  }

  const totals = resourceTypeTotals((await res.json()) as Parameters);

  return {
    resourceTypeTotals: totals,
    streamed: {
      start: Date.now(),
      demographics:
        patientTotal(totals) > 0 ? fetchDemographics(fetch, new Date().getFullYear()) : undefined
    }
  };
};
