import { describe, it, expect, vi } from 'vitest';
import * as navigation from '$app/navigation';
import { fetchJsonStreamed } from '$lib/fetch.js';
import { signInPath } from '$lib/sign-in.js';

// `fetch.test.ts` deliberately runs with `browser === false` (see its
// comment at the bottom of the `fetchJsonStreamed` describe block).
// `vi.mock('$app/environment')` is module-scoped, so the browser branch
// cannot be toggled per-test within that file — exercise it here instead,
// mirroring the mock setup used by `sign-in-navigation.test.ts`.
vi.mock('$app/environment', () => ({ browser: true }));
vi.mock('$app/navigation', () => ({ goto: vi.fn() }));
vi.mock('$app/state', () => ({
  navigating: { to: null },
  page: { url: new URL('http://localhost/') }
}));

const goto = vi.mocked(navigation.goto);

describe('fetchJsonStreamed browser test', () => {
  it('navigates to the sign-in page and never settles the returned promise on a 401', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));

    let settled = false;
    fetchJsonStreamed(fetch, 'http://localhost/foo', 'session expired').then(
      () => {
        settled = true;
      },
      () => {
        settled = true;
      }
    );

    // Flush both the microtask queue (the promise chain inside
    // `fetchJsonStreamed`/`redirectIfSessionExpired`/`gotoSignIn`) and any
    // real timers, giving a settling promise every chance to actually
    // settle before asserting it did not.
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(settled).toBe(false);
    expect(goto).toHaveBeenCalledWith(`${signInPath}?redirect=%2F`);
  });
});
