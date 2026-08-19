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

// Issue #4082: an expired session has to be recovered from the response to
// a plain `fetch` call, not just from a document load — the `Patient` page's
// load function fetches its search params before rendering anything.
test('An in-app navigation with an expired session goes to the Sign-In page and returns to the intended page afterwards', async ({
  page,
  context
}) => {
  // Simulates the session expiring while the app is open: the session
  // cookie is gone, but — unlike an explicit sign-out — the running app
  // has no way of knowing that ahead of time, so the very next navigation
  // is the one that has to discover it and recover from it.
  await context.clearCookies();

  // Client-side navigation (not a full page load), which is what exercises
  // the plain `fetch` call in `[type=type]/+page.ts`'s load function.
  await page.getByRole('link', { name: 'Patient', exact: true }).click();

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
