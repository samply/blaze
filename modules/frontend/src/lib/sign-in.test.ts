import { describe, it, expect } from 'vitest';
import { signInPath, signInUrl, sessionExpired } from '$lib/sign-in.js';

describe('signInUrl test', () => {
  it('returns the sign-in path without a redirect param when target is undefined', () => {
    expect(signInUrl(undefined)).toBe(signInPath);
  });

  it('appends the encoded path and query of the target', () => {
    expect(signInUrl(new URL('http://localhost/Patient?_count=50'))).toBe(
      `${signInPath}?redirect=%2FPatient%3F_count%3D50`
    );
  });

  it('does not lose the query after a round-trip through URLSearchParams', () => {
    const target = new URL('http://localhost/Patient?_count=50&_summary=true');
    const url = new URL(signInUrl(target), 'http://localhost');
    expect(url.searchParams.get('redirect')).toBe('/Patient?_count=50&_summary=true');
  });

  it('does not include the origin', () => {
    expect(signInUrl(new URL('http://localhost/Patient'))).not.toContain('localhost');
  });
});

describe('sessionExpired test', () => {
  it('is true on 401', () => {
    expect(sessionExpired({ status: 401 })).toBe(true);
  });

  it('is false on 200', () => {
    expect(sessionExpired({ status: 200 })).toBe(false);
  });

  it('is false on other error statuses', () => {
    expect(sessionExpired({ status: 404 })).toBe(false);
    expect(sessionExpired({ status: 500 })).toBe(false);
  });
});
