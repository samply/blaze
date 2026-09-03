import { expect, test } from '@playwright/test';

test.beforeEach('Sign In', async ({ page }) => {
  await page.goto('/fhir');

  // Blaze Sign-In Page
  await expect(page).toHaveTitle('Sign-In - Blaze');
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click();

  // Keycloak Sign-In Page
  await expect(page).toHaveTitle('Sign in to Keycloak');
  await page.getByLabel('Username or email').fill('john');
  await page.getByLabel('Password', { exact: true }).fill('insecure');
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page).toHaveTitle('Home - Blaze');
});

// A session that is gone has to be recovered from a client-side
// navigation, not only from a full page load.
//
// This covers the `handleAuthorization` half: without the session cookie,
// there is no session at all, so the redirect is thrown before any backend
// request is made. What it exercises that a unit test cannot is the envelope
// — SvelteKit fetches `__data.json` for a client-side navigation, and the
// client router has to turn the redirect it finds there into a navigation.
//
// The other half of #4082 — a session Auth.js still considers valid whose
// token the backend rejects, which is what `handleFetch` recovers from — is
// covered by `src/hooks.server.test.ts`. Reaching it from here would mean
// revoking the Keycloak session behind the app's back.
test('an in-app navigation without a session goes to the sign-in page and returns afterwards', async ({
  page,
  context
}) => {
  // Simulates the session ending while the app is open: the cookie is gone,
  // but the running app has no way of knowing that ahead of time, so the very
  // next navigation is the one that has to discover and recover from it.
  await context.clearCookies();

  // A client-side navigation, not a full page load. This is what issues the
  // `__data.json` request for the load of `[type=type]/+page.server.ts`.
  await page.getByRole('link', { name: 'Patient' }).click();

  // Blaze Sign-In Page
  await expect(page).toHaveTitle('Sign-In - Blaze');

  // The intended destination is remembered for after signing in.
  await expect(page).toHaveURL(/redirect=.*Patient/);

  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click();

  // Keycloak Sign-In Page
  await expect(page).toHaveTitle('Sign in to Keycloak');
  await page.getByLabel('Username or email').fill('john');
  await page.getByLabel('Password', { exact: true }).fill('insecure');
  await page.getByRole('button', { name: 'Sign In' }).click();

  // Back on the page the user originally navigated to.
  await expect(page).toHaveTitle('Patient - Blaze');
});
