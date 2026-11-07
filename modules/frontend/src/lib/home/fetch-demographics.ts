import type { MeasureReport, OperationOutcome } from 'fhir/r4';
import { error } from '@sveltejs/kit';
import { backendUrl } from '#lib/backend.js';
import { demographicsParameters, parseDemographics, type Demographics } from './demographics.js';

async function diagnostics(res: Response): Promise<string | undefined> {
  try {
    return ((await res.json()) as OperationOutcome).issue?.[0]?.diagnostics;
  } catch {
    return undefined;
  }
}

/**
 * Evaluates the patient demographics measure inline via `$evaluate-measure`.
 *
 * Fails with the diagnostics of the OperationOutcome returned by the backend.
 */
export async function fetchDemographics(
  fetch: typeof window.fetch,
  year: number
): Promise<Demographics> {
  const res = await fetch(backendUrl('/Measure/$evaluate-measure'), {
    method: 'POST',
    headers: { Accept: 'application/fhir+json', 'Content-Type': 'application/fhir+json' },
    body: JSON.stringify(demographicsParameters(year))
  });

  if (!res.ok) {
    error(
      res.status,
      (await diagnostics(res)) ?? 'Error while evaluating the patient demographics.'
    );
  }

  return parseDemographics((await res.json()) as MeasureReport);
}
