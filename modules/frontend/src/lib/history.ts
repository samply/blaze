import { error } from '@sveltejs/kit';
import { processParams } from '#lib/util.js';
import type { Bundle, FhirResource } from 'fhir/r4';
import type { SummaryState } from '#lib/summary.js';
import { summaryFromUrl } from '#lib/summary.js';

/**
 * The query string of an instance history request.
 *
 * An instance history is requested as a whole rather than through the paged
 * search machinery, so `_count` is passed without a value and the summary mode
 * is the only part that varies. Only a summary mode is expressed, because
 * absent already means full, matching Blaze's own default.
 */
export function instanceHistoryQuery(summary: SummaryState): string {
  return summary.mode === 'summary' ? '_count&_summary=true' : '_count';
}

/**
 * Resolves the summary state of a history result set from the URL's
 * `_summary` param.
 *
 * Delegates to `summaryFromUrl` for all modes except `count`, which is
 * treated as unknown because `fhir-util/summary` honours only `"true"` on a
 * history request and returns full entries for `count`. This prevents the UI
 * from showing a wrong "Count mode" banner while the server is returning
 * full resources.
 */
export function historySummaryFromUrl(params: URLSearchParams): SummaryState {
  const summary = summaryFromUrl(params);
  return summary.mode === 'count' ? {} : summary;
}

/**
 * Loads a history bundle from the given URL, applying the shared
 * default-count and _summary handling.
 *
 * On a non-ok response it aborts with a SvelteKit error carrying `errorMessage`.
 */
export async function fetchHistoryBundle(
  fetch: typeof window.fetch,
  url: string,
  searchParams: URLSearchParams,
  errorMessage: string
): Promise<Bundle<FhirResource>> {
  const res = await fetch(`${url}?${processParams(searchParams)}`, {
    headers: { Accept: 'application/fhir+json' }
  });

  if (!res.ok) {
    error(res.status, errorMessage);
  }

  return (await res.json()) as Bundle<FhirResource>;
}
