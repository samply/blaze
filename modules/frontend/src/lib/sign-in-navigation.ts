import { redirect } from '@sveltejs/kit';
import { goto } from '$app/navigation';
import { browser } from '$app/environment';
import { navigating, page } from '$app/state';
import { sessionExpired, signInUrl } from '$lib/sign-in.js';

/**
 * Client-side helpers for reacting to an expired session (see
 * `$lib/sign-in.ts`). Kept separate from that module so that
 * `hooks.server.ts` — a server-only file that only needs `signInUrl` — does
 * not pull `$app/navigation` and `$app/state` into the server bundle.
 */

function currentTarget(): URL | undefined {
  // `$app/state` can only be read in the browser here — on the server it is
  // only readable from component render context, not from load functions.
  // The server-side case is covered before it gets here: `handleFetch` in
  // `hooks.server.ts` turns an unauthorized backend response into the
  // redirect itself, where `event.url` gives it the return-to target.
  return browser ? (navigating.to?.url ?? page.url) : undefined;
}

/**
 * Call after every `fetch` in a universal load function. Redirects to the
 * sign-in page, remembering the current navigation target, if `res`
 * indicates that the session has expired.
 */
export function ensureSignedIn(res: Response): void {
  if (sessionExpired(res)) {
    redirect(307, signInUrl(currentTarget()));
  }
}

/**
 * Like `ensureSignedIn`, but for use where a thrown `redirect` would not
 * reach SvelteKit's navigation handling: component event handlers, and
 * streamed load data (returned as an un-awaited promise and consumed via
 * `{#await ... }{:catch}`), where a rejection is caught locally instead of
 * causing a navigation.
 *
 * A no-op on the server, where `goto` is unavailable — see
 * `redirectIfSessionExpired` for how callers should handle that case.
 */
export async function gotoSignIn(): Promise<void> {
  if (browser) {
    // signInUrl is not a route `resolve()` can build: it is an external
    // (from the router's point of view) URL carrying the return-to target.
    // eslint-disable-next-line svelte/no-navigation-without-resolve
    await goto(signInUrl(currentTarget()));
  }
}

/**
 * For the same contexts as `gotoSignIn`: navigates directly if the session
 * has expired and reports whether it did.
 *
 * On a `true` result, the caller should return a promise that never
 * resolves (e.g. `new Promise(() => {})`), so that whatever `{#await}`
 * block is watching it stays pending until the navigation tears down the
 * component, instead of settling with a bogus value.
 *
 * On the server `goto` is unavailable, so this always returns `false`
 * there and the caller should fall back to its normal error handling —
 * a streamed value must settle to avoid hanging the response.
 */
export async function redirectIfSessionExpired(res: Response): Promise<boolean> {
  if (sessionExpired(res) && browser) {
    await gotoSignIn();
    return true;
  }

  return false;
}
