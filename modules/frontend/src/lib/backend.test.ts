import { describe, it, expect } from 'vitest';
import { backendUrl } from '#lib/backend.js';
// @ts-expect-error the internal module has no types of its own
import { with_request_store } from '@sveltejs/kit/internal/server';

/**
 * Calls `fn` while the server renders the page at `url`, as `backendUrl` is
 * called from a load function.
 */
function duringRequest<T>(url: string, isDataRequest: boolean, fn: () => T): T {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const store = { event: { url: new URL(url), isDataRequest }, state: {} } as any;
  return with_request_store(store, fn);
}

describe('backendUrl test', () => {
  it('prefixes the path with the base path', () => {
    expect(backendUrl('/Patient')).toBe('/fhir/Patient');
  });

  it('appends a string query', () => {
    expect(backendUrl('/Patient', '_count=50')).toBe('/fhir/Patient?_count=50');
  });

  it('appends a URLSearchParams query', () => {
    const query = new URLSearchParams({ _count: '50', _summary: 'true' });

    expect(backendUrl('/Patient', query)).toBe('/fhir/Patient?_count=50&_summary=true');
  });

  it('omits the question mark for an empty URLSearchParams', () => {
    expect(backendUrl('/Patient', new URLSearchParams())).toBe('/fhir/Patient');
  });

  it('omits the question mark for an empty string query', () => {
    expect(backendUrl('/Patient', '')).toBe('/fhir/Patient');
  });

  // `handleFetch` swaps this app's origin for the backend base URL, so the
  // URL has to stay root-relative rather than carry an origin of its own.
  it('returns a root-relative URL', () => {
    expect(backendUrl('/Patient', '_count=50').startsWith('/')).toBe(true);
  });

  // `event.fetch` resolves the URL against `event.url`, which for a
  // `__data.json` request has one segment less than the URL the browser is at.
  // A URL relative to the latter would lose the base path.
  it('stays root-relative while the server renders a page', () => {
    expect(
      duringRequest('http://localhost/fhir/Patient/0', false, () => backendUrl('/Patient'))
    ).toBe('/fhir/Patient');
  });

  it('stays root-relative while the server answers a data request', () => {
    expect(
      duringRequest('http://localhost/fhir/Patient/0', true, () => backendUrl('/Patient'))
    ).toBe('/fhir/Patient');
  });
});
