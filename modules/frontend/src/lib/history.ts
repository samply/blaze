import { error, type NumericRange } from '@sveltejs/kit';
import { processParams } from '$lib/util.js';
import { transformBundle, type FhirObjectBundle } from '$lib/resource/resource-card.js';

/**
 * Loads a history bundle from the given URL, applying the shared summary-mode
 * and default-count handling and transforming the result for rendering.
 *
 * On a non-ok response it aborts with the given SvelteKit error body.
 */
export async function fetchHistoryBundle(
  fetch: typeof window.fetch,
  url: string,
  searchParams: URLSearchParams,
  summary: boolean,
  errorBody: App.Error | string
): Promise<FhirObjectBundle> {
  const res = await fetch(`${url}?${processParams(searchParams, summary)}`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status as NumericRange<400, 599>, errorBody);
  }

  return transformBundle(fetch, await res.json());
}
