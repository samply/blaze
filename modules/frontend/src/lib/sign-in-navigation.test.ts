import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isRedirect } from '@sveltejs/kit';
import * as navigation from '$app/navigation';
import * as state from '$app/state';
import { signInPath } from '$lib/sign-in.js';
import { ensureSignedIn, gotoSignIn, redirectIfSessionExpired } from '$lib/sign-in-navigation.js';

vi.mock('$app/environment', () => ({ browser: true }));
vi.mock('$app/navigation', () => ({ goto: vi.fn() }));
vi.mock('$app/state', () => ({
  navigating: { to: null },
  page: { url: new URL('http://localhost/') }
}));

const goto = vi.mocked(navigation.goto);
// The real `$app/state` types are wider (and readonly) than what the mock
// above provides; narrow the mocked bindings to the shape these tests
// actually drive.
const navigating = state.navigating as unknown as { to: { url: URL } | null };
const page = state.page as unknown as { url: URL };

beforeEach(() => {
  goto.mockClear();
  navigating.to = null;
  page.url = new URL('http://localhost/fallback');
});

function expired() {
  return new Response(null, { status: 401 });
}

function ok() {
  return new Response(null, { status: 200 });
}

describe('ensureSignedIn test', () => {
  it('does nothing when the session has not expired', () => {
    expect(() => ensureSignedIn(ok())).not.toThrow();
  });

  it('redirects to the sign-in page carrying the pending navigation target', () => {
    navigating.to = { url: new URL('http://localhost/Patient?_count=50') };

    try {
      ensureSignedIn(expired());
      expect.fail('expected ensureSignedIn to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
      expect((e as { location: string }).location).toBe(
        `${signInPath}?redirect=%2FPatient%3F_count%3D50`
      );
    }
  });

  it('falls back to the current page url when there is no pending navigation', () => {
    page.url = new URL('http://localhost/Observation');

    try {
      ensureSignedIn(expired());
      expect.fail('expected ensureSignedIn to throw');
    } catch (e) {
      expect((e as { location: string }).location).toBe(`${signInPath}?redirect=%2FObservation`);
    }
  });
});

describe('gotoSignIn test', () => {
  it('navigates to the sign-in page carrying the current target', async () => {
    navigating.to = { url: new URL('http://localhost/Patient') };

    await gotoSignIn();

    expect(goto).toHaveBeenCalledWith(`${signInPath}?redirect=%2FPatient`);
  });
});

describe('redirectIfSessionExpired test', () => {
  it('navigates to the sign-in page and returns true on a 401', async () => {
    navigating.to = { url: new URL('http://localhost/Patient') };

    await expect(redirectIfSessionExpired(expired())).resolves.toBe(true);

    expect(goto).toHaveBeenCalledWith(`${signInPath}?redirect=%2FPatient`);
  });

  it('does nothing and returns false on a non-401 response', async () => {
    await expect(redirectIfSessionExpired(ok())).resolves.toBe(false);

    expect(goto).not.toHaveBeenCalled();
  });
});
