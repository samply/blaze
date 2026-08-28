import { describe, it, expect } from 'vitest';
import { backendUrl } from '$lib/backend.js';

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
});
