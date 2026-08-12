import { expect, type Locator, type Page, test } from '@playwright/test';

/**
 * End-to-end coverage of the summary mode control.
 *
 * The invariant under test is that `_summary` is absent from the URL exactly
 * when full mode is on. The param shows up to request summary mode
 * (`_summary=true`), count mode (`_summary=count`), or to carry a value the
 * control doesn't own (like `text`). Absent means full, which is what Blaze
 * returns without `_summary`, matching the server's own default, so every
 * URL the app produces can be copied into curl and yields the result set
 * shown.
 *
 * App-generated links always carry `?_summary=true`, so the browsing default
 * is summary. A bare, hand-typed or deep-linked URL means full and stays that
 * way — there is no redirect or persistence.
 *
 * The control itself is a plain navigation (a `<nav>` of `<a>` links), not a
 * form, so choosing a mode works even before the client bundle has hydrated.
 */

/** A patient that exists exactly once in the test data. */
const patientIdentifier = 'fcd31792-91d8-982e-8e28-ef1eb284e260';

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

function resultControl(page: Page): Locator {
  return page.getByRole('navigation', { name: 'Result' });
}

function modeLink(page: Page, mode: 'Summary' | 'Full' | 'Count'): Locator {
  return resultControl(page).getByRole('link', { name: mode, exact: true });
}

function subsettedBadges(page: Page): Locator {
  return page.getByRole('note').filter({ hasText: 'subsetted' });
}

function showAllElementsLink(page: Page): Locator {
  return page.getByRole('link', { name: 'Show all elements' });
}

function showResourcesLink(page: Page): Locator {
  return page.getByRole('link', { name: 'Show resources' });
}

/**
 * Selects the given mode by following its link.
 *
 * Waits for hydration first: the history routes render fully from SSR, so a
 * click can land while the client is still redoing that page's own load
 * (SvelteKit reruns a universal `load` on hydration) in the background. That
 * leftover fetch races the navigation triggered by the click, which — under
 * WebKit against this dev environment's self-signed certificate — reliably
 * aborts the click's navigation instead of just slowing it down. Waiting for
 * the network to go idle first avoids starting the click into that race.
 */
async function selectMode(page: Page, mode: 'Summary' | 'Full' | 'Count'): Promise<void> {
  await waitForHydration(page);
  const link = modeLink(page, mode);
  const href = await link.getAttribute('href');
  await link.click();
  if (href !== null) {
    await page.waitForURL(href);
  }
  await expect(modeLink(page, mode)).toHaveAttribute('aria-current', 'page');
}

/**
 * Waits until the app has hydrated.
 *
 * The search form is a real form and needs the client bundle to submit
 * without a full page reload; an early interaction is undone by hydration.
 */
async function waitForHydration(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
}

/** Searches the current type page for the patient with {@link patientIdentifier}. */
async function searchForPatient(page: Page): Promise<void> {
  await waitForHydration(page);
  await page.getByLabel('Search Param', { exact: true }).selectOption('identifier');
  await page.getByLabel('Search Value').fill(patientIdentifier);
  await page.getByRole('button', { name: 'Search', exact: true }).click();

  await expect(page.getByRole('note').filter({ hasText: /Total: 1$/ })).toBeVisible();
}

/** Asserts that control, banner and badges all agree that summary mode is on. */
async function expectSummaryMode(page: Page): Promise<void> {
  await expect(modeLink(page, 'Summary')).toHaveAttribute('aria-current', 'page');
  await expect(page.getByText('some elements are hidden')).toBeVisible();
  await expect(subsettedBadges(page).first()).toBeVisible();
}

/** Asserts that control, banner and badges all agree that full mode is on. */
async function expectFullMode(page: Page): Promise<void> {
  await expect(modeLink(page, 'Full')).toHaveAttribute('aria-current', 'page');
  await expect(page.getByText('some elements are hidden')).toBeHidden();
  await expect(subsettedBadges(page)).toHaveCount(0);
}

test.describe('Search', () => {
  test('opening search from the home page defaults to summary mode', async ({ page }) => {
    await page.goto('/fhir');
    await page.getByRole('link', { name: 'Patient', exact: true }).click();

    await expect(page).toHaveTitle('Patient - Blaze');
    await expect(page).toHaveURL(/\/fhir\/Patient\?_summary=true$/);
    await expectSummaryMode(page);
  });

  test('drops _summary from the URL when Full is chosen', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=true');
    await expectSummaryMode(page);

    await selectMode(page, 'Full');

    await expect(page).toHaveURL(/\/fhir\/Patient$/);
    await expectFullMode(page);
  });

  test('bare deep links mean full mode and stay bare', async ({ page }) => {
    await page.goto('/fhir/Patient');

    await expect(page).toHaveURL(/\/fhir\/Patient$/);
    await expectFullMode(page);

    // a reload keeps the bare URL's meaning (full), so it is shareable
    await page.reload();
    await expect(page).toHaveURL(/\/fhir\/Patient$/);
    await expectFullMode(page);
  });

  test('app-generated links always request summary mode, regardless of a prior choice', async ({
    page
  }) => {
    await page.goto('/fhir/Patient?_summary=true');
    await selectMode(page, 'Full');
    await expect(page).toHaveURL(/\/fhir\/Patient$/);

    // the home table link carries ?_summary=true, undoing the Full choice
    await page.goto('/fhir');
    await page.getByRole('link', { name: 'Patient', exact: true }).click();

    await expect(page).toHaveURL(/\/fhir\/Patient\?_summary=true$/);
    await expectSummaryMode(page);
  });

  test('submitting a search preserves the URL _summary value', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=true');
    await searchForPatient(page);
    await expect(page).toHaveURL(/identifier=fcd31792-91d8.*_summary=true$/s);
  });

  test('submitting a search from a bare URL keeps the URL bare', async ({ page }) => {
    await page.goto('/fhir/Patient');
    await searchForPatient(page);
    await expect(page).toHaveURL(/identifier=fcd31792-91d8/);
    await expect(page).not.toHaveURL(/_summary/);
  });

  test('banner "Show all elements" escapes to full mode', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=true');
    await expectSummaryMode(page);

    await showAllElementsLink(page).click();

    await expect(page).toHaveURL(/\/fhir\/Patient$/);
    await expectFullMode(page);
  });

  test('count mode adds ?_summary=count and shows a "Count mode" banner', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=count');

    await expect(page).toHaveURL(/\/fhir\/Patient\?_summary=count$/);
    await expect(
      page.getByText('Count mode — no resources are returned, just the total')
    ).toBeVisible();
    await expect(modeLink(page, 'Count')).toHaveAttribute('aria-current', 'page');
  });

  test('banner "Show resources" escapes count mode back to summary', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=count');

    await expect(modeLink(page, 'Count')).toHaveAttribute('aria-current', 'page');
    await showResourcesLink(page).click();

    await expect(page).toHaveURL(/\/fhir\/Patient\?_summary=true$/);
    await expectSummaryMode(page);
  });

  test('subsequent search pages render a fixed control with no links and no escape hatch', async ({
    page
  }) => {
    await page.goto('/fhir/Patient?_summary=true');
    await expectSummaryMode(page);

    await waitForHydration(page);
    await page.getByRole('link', { name: 'Next' }).first().click();
    await expect(page.getByText('Non-First Page')).toBeVisible();

    // the page link determines the mode, so it is shown but not offered as a link
    await expect(page.getByText('some elements are hidden')).toBeVisible();
    await expect(resultControl(page).getByRole('link')).toHaveCount(0);
    await expect(showAllElementsLink(page)).toHaveCount(0);
  });

  test('unknown _summary values render a control with no current mode', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=text');

    await expect(page).toHaveURL(/\/fhir\/Patient\?_summary=text$/);
    await expect(resultControl(page)).toBeVisible();
    await expect(resultControl(page).locator('[aria-current="page"]')).toHaveCount(0);

    // picking Summary escapes to an owned mode
    await selectMode(page, 'Summary');
    await expect(page).toHaveURL(/\/fhir\/Patient\?_summary=true$/);
    await expectSummaryMode(page);
  });
});

test.describe('History', () => {
  /** Opens the instance history of a single, deterministically chosen patient. */
  async function goToPatientInstanceHistory(page: Page): Promise<void> {
    await page.goto('/fhir/Patient?_summary=true');
    await searchForPatient(page);
    await page.getByRole('link', { name: 'Patient/', exact: false }).first().click();

    await expect(page).toHaveTitle(/Patient\/\w+ - Blaze/);
    await page.getByTitle('Go to resource history').click();
  }

  test('the History button defaults to summary mode', async ({ page }) => {
    await page.goto('/fhir/Patient?_summary=true');
    await page.getByTitle('Patient History').click();

    await expect(page).toHaveTitle('History - Patient - Blaze');
    await expect(page).toHaveURL(/\/fhir\/Patient\/_history\?_summary=true$/);
    await expectSummaryMode(page);
  });

  test('control offers only Summary and Full for history, no Count', async ({ page }) => {
    await page.goto('/fhir/Patient/_history?_summary=true');

    await expect(modeLink(page, 'Summary')).toBeVisible();
    await expect(modeLink(page, 'Full')).toBeVisible();
    await expect(resultControl(page).getByText('Count', { exact: true })).toHaveCount(0);
  });

  test('drops _summary from the URL when Full is chosen', async ({ page }) => {
    await page.goto('/fhir/Patient/_history?_summary=true');
    await expectSummaryMode(page);

    await selectMode(page, 'Full');

    await expect(page).toHaveURL(/\/fhir\/Patient\/_history$/);
    await expectFullMode(page);
  });

  test('bare deep links mean full mode and stay bare', async ({ page }) => {
    await page.goto('/fhir/Patient/_history');

    await expect(page).toHaveURL(/\/fhir\/Patient\/_history$/);
    await expectFullMode(page);

    await page.reload();
    await expect(page).toHaveURL(/\/fhir\/Patient\/_history$/);
    await expectFullMode(page);
  });

  test('instance history defaults to summary mode', async ({ page }) => {
    await goToPatientInstanceHistory(page);

    await expect(page).toHaveURL(/\/fhir\/Patient\/[^/?]+\/_history\?_summary=true$/);
    await expectSummaryMode(page);
  });

  test('instance history keeps a Full choice across a reload', async ({ page }) => {
    // the "Go to resource history" button always opens summary mode, so the
    // Full choice has to be made on the history page itself
    await goToPatientInstanceHistory(page);
    await expectSummaryMode(page);

    await selectMode(page, 'Full');
    await expect(page).toHaveURL(/\/fhir\/Patient\/[^/?]+\/_history$/);
    await expectFullMode(page);

    // a reload keeps the bare URL's meaning (full), so it is shareable
    await page.reload();
    await expect(page).toHaveURL(/\/fhir\/Patient\/[^/?]+\/_history$/);
    await expectFullMode(page);
  });
});
