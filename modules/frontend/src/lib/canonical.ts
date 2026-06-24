// Central registry of Blaze-minted FHIR canonical URLs for the frontend.
//
// Blaze migrated its canonical base from the legacy `samply.github.io` host to
// the owned `blaze-server.org` domain. To stay backward compatible with
// already-stored data, the frontend emits the current canonical but keeps
// accepting the legacy one on read (mirrors `blaze.fhir.canonical` on the
// backend).

export const base = 'https://blaze-server.org/fhir';

export const oldBase = 'https://samply.github.io/blaze/fhir';

/** Builds the current canonical URL for `path` (e.g. `CodeSystem/JobType`). */
export function url(path: string): string {
  return `${base}/${path}`;
}

/** Returns the legacy-base form of `u` when it is on the current `base`, else `undefined`. */
function legacyUrl(u: string): string | undefined {
  return u.startsWith(base) ? oldBase + u.slice(base.length) : undefined;
}

/**
 * Returns `true` if `value` equals the current canonical `canonicalUrl` or its
 * legacy equivalent. `canonicalUrl` must be a canonical on the current `base`.
 * Use on read paths to accept both the new and the legacy form of a
 * Blaze-minted canonical.
 */
export function matches(canonicalUrl: string, value: string | undefined): boolean {
  return value !== undefined && (value === canonicalUrl || value === legacyUrl(canonicalUrl));
}
