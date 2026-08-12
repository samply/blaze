import type { Bundle, BundleEntry } from 'fhir/r4';
import type { SummaryMode } from '$lib/summary.js';

const subsettedSystem = 'http://terminology.hl7.org/CodeSystem/v3-ObservationValue';

/**
 * Whether the resource of the given bundle entry is a subsetted (summary)
 * representation, according to its SUBSETTED meta tag.
 */
export function isSubsetted(entry: BundleEntry): boolean {
  const tags = entry.resource?.meta?.tag;
  return tags?.filter((c) => c.system === subsettedSystem)[0]?.code === 'SUBSETTED';
}

/**
 * The summary mode a bundle's resources reveal, or undefined if the bundle
 * gives no evidence of its summary mode.
 *
 * Bundles without any resource, like `_summary=count` results or history
 * bundles of deletions only, give no evidence of their summary mode. Count is
 * never inferred, because an empty bundle is indistinguishable from an empty
 * result set.
 */
export function bundleSummaryMode(bundle: Bundle): SummaryMode | undefined {
  const entries = bundle.entry?.filter((entry) => entry.resource !== undefined) ?? [];
  if (entries.length === 0) return undefined;
  return entries.some(isSubsetted) ? 'summary' : 'full';
}
