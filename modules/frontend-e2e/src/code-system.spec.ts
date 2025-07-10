import { expect, type Locator, type Page, test } from '@playwright/test';

function breadcrumbItem(page: Page, text: string): Locator {
  return page.getByLabel('Breadcrumb').getByRole('listitem').filter({ hasText: text });
}

test.beforeEach('Sign In', async ({ page }) => {
  await page.goto('/fhir/CodeSystem');

  // Blaze Sign-In Page
  await expect(page).toHaveTitle('Sign-In - Blaze');
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click();

  // Keycloak Sign-In Page
  await expect(page).toHaveTitle('Sign in to Keycloak');
  await page.getByLabel('Username or email').fill('john');
  await page.getByLabel('Password', { exact: true }).fill('insecure');
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page).toHaveTitle('CodeSystem - Blaze');
  await expect(breadcrumbItem(page, 'CodeSystem')).toBeVisible();
  await expect(page.getByText('Total:')).toBeVisible();
});

test('Search Page', async ({ page }) => {
  await expect(page.getByTitle('CodeSystem History')).toBeVisible();
  await expect(page.getByTitle('CodeSystem Metadata')).toBeVisible();
  await expect(page.getByText('Total:')).toBeVisible();
});

test('Search for LOINC', async ({ page }) => {
  await page.goto('/fhir/CodeSystem?url=http://loinc.org');

  await expect(breadcrumbItem(page, 'CodeSystem')).toBeVisible();

  await expect(page.getByRole('link', { name: 'LOINC Code System v2.78' })).toBeVisible();
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Url http://loinc.org' })
  ).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Version 2.78' })).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Name LOINC' })).toBeVisible();
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Title LOINC Code System' })
  ).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Status active' })).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Experimental false' })).toBeVisible();
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Publisher Regenstrief Institute, Inc.' })
  ).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Content not-present' })).toBeVisible();

  await page.getByRole('link', { name: 'LOINC Code System v2.78' }).click();

  await expect(breadcrumbItem(page, 'CodeSystem')).toBeVisible();
  await expect(breadcrumbItem(page, 'LOINC Code System v2.78')).toBeVisible();
  await expect(page.getByRole('link', { name: 'LOINC Code System v2.78' })).toBeVisible();
});

test.describe('$validate-code', () => {
  test.describe('LOINC 718-7', () => {
    test('type-level', async ({ page }) => {
      await page.getByRole('button', { name: 'Operations' }).click();
      await page.getByRole('menuitem', { name: '$validate-code' }).click();

      await expect(breadcrumbItem(page, 'CodeSystem')).toBeVisible();
      await expect(breadcrumbItem(page, '$validate-code')).toBeVisible();

      await page.getByRole('heading', { name: 'Parameters' }).click();
      await page.getByLabel('URL').fill('http://loinc.org');
      await page.getByLabel('Code').fill('718-7');
      await page.getByRole('button', { name: 'Submit' }).click();

      await expect(page.getByRole('listitem').filter({ hasText: 'Result true' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'Display Hemoglobin [Mass/volume] in Blood' })
      ).toBeVisible();
      await expect(page.getByRole('listitem').filter({ hasText: 'Code 718-7' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'System http://loinc.org' })
      ).toBeVisible();
      await expect(page.getByRole('listitem').filter({ hasText: 'Version 2.78' })).toBeVisible();
    });

    test('instance-level', async ({ page }) => {
      await page.goto('/fhir/CodeSystem?url=http://loinc.org');

      await page.getByRole('link', { name: 'LOINC Code System v2.78' }).click();

      await expect(breadcrumbItem(page, 'LOINC Code System v2.78')).toBeVisible();
      await page.getByRole('button', { name: 'Operations' }).click();
      await page.getByRole('menuitem', { name: '$validate-code' }).click();

      await page.getByRole('heading', { name: 'LOINC Code System v2.78' }).click();

      await expect(breadcrumbItem(page, 'CodeSystem')).toBeVisible();
      await expect(breadcrumbItem(page, 'LOINC Code System v2.78')).toBeVisible();
      await expect(breadcrumbItem(page, '$validate-code')).toBeVisible();

      await page.getByRole('heading', { name: 'Parameters' }).click();
      await page.getByLabel('Code').fill('718-7');
      await page.getByRole('button', { name: 'Submit' }).click();

      await expect(page.getByRole('listitem').filter({ hasText: 'Result true' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'Display Hemoglobin [Mass/volume] in Blood' })
      ).toBeVisible();
      await expect(page.getByRole('listitem').filter({ hasText: 'Code 718-7' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'System http://loinc.org' })
      ).toBeVisible();
      await expect(page.getByRole('listitem').filter({ hasText: 'Version 2.78' })).toBeVisible();
    });
  });
});
