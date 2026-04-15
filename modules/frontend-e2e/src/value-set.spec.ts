import { expect, type Locator, type Page, test } from '@playwright/test';

function breadcrumbItem(page: Page, text: string): Locator {
  return page.getByLabel('Breadcrumb').getByRole('listitem').filter({ hasText: text });
}

test.beforeEach('Sign In', async ({ page }) => {
  await page.goto('/fhir/ValueSet');

  // Blaze Sign-In Page
  await expect(page).toHaveTitle('Sign-In - Blaze');
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click();

  // Keycloak Sign-In Page
  await expect(page).toHaveTitle('Sign in to Keycloak');
  await page.getByLabel('Username or email').fill('john');
  await page.getByLabel('Password', { exact: true }).fill('insecure');
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page).toHaveTitle('ValueSet - Blaze');
  await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();
  await expect(page.getByText('Total:')).toBeVisible();
});

test('Search Page', async ({ page }) => {
  await expect(page.getByTitle('ValueSet History')).toBeVisible();
  await expect(page.getByTitle('ValueSet Metadata')).toBeVisible();
  await expect(page.getByText('Total:')).toBeVisible();
});

test('Search for Laborbereich', async ({ page }) => {
  await page.goto(
    '/fhir/ValueSet?url=https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/Laborbereich'
  );

  await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();

  await expect(
    page.getByRole('link', { name: 'MII VS Labor Laborbereich v2026.0.0' })
  ).toBeVisible();
  await expect(
    page.getByRole('listitem').filter({
      hasText:
        'Url https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/Laborbereich'
    })
  ).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Version 2026.0.0' })).toBeVisible();
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Name MII_VS_Labor_Laborbereich' })
  ).toBeVisible();
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Title MII VS Labor Laborbereich' })
  ).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Status active' })).toBeVisible();

  await page.getByRole('link', { name: 'MII VS Labor Laborbereich v2026.0.0' }).click();

  await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();
  await expect(breadcrumbItem(page, 'MII VS Labor Laborbereich v2026.0.0')).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'MII VS Labor Laborbereich v2026.0.0' })
  ).toBeVisible();
});

test.describe('$validate-code', () => {
  test.describe('MII VS Labor Laborbereich v2026.0.0', () => {
    test('type-level', async ({ page }) => {
      await page.getByRole('button', { name: 'Operations' }).click();
      await page.getByRole('menuitem', { name: '$validate-code' }).click();

      await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();
      await expect(breadcrumbItem(page, '$validate-code')).toBeVisible();

      await page.getByRole('heading', { name: 'Parameters' }).click();
      await page
        .getByLabel('ValueSet URL')
        .fill(
          'https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/Laborbereich'
        );
      await page.getByLabel('System', { exact: true }).fill('http://loinc.org');
      await page.getByLabel('Code').fill('18719-5');
      await page.getByRole('button', { name: 'Submit' }).click();

      await expect(page.getByRole('listitem').filter({ hasText: 'Result true' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'Display CHEMISTRY STUDIES' })
      ).toBeVisible();
      await expect(page.getByRole('listitem').filter({ hasText: 'Code 18719-5' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'System http://loinc.org' })
      ).toBeVisible();
    });

    test('instance-level', async ({ page }) => {
      await page.goto(
        '/fhir/ValueSet?url=https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/Laborbereich'
      );

      await page.getByRole('link', { name: 'MII VS Labor Laborbereich v2026.0.0' }).click();

      await expect(breadcrumbItem(page, 'MII VS Labor Laborbereich v2026.0.0')).toBeVisible();
      await page.getByRole('button', { name: 'Operations' }).click();
      await page.getByRole('menuitem', { name: '$validate-code' }).click();

      await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();
      await expect(breadcrumbItem(page, 'MII VS Labor Laborbereich v2026.0.0')).toBeVisible();
      await expect(breadcrumbItem(page, '$validate-code')).toBeVisible();

      await page.getByRole('heading', { name: 'Parameters' }).click();
      await page.getByLabel('Code').fill('18719-5');
      await page.getByLabel('System', { exact: true }).fill('http://loinc.org');
      await page.getByRole('button', { name: 'Submit' }).click();

      await expect(page.getByRole('listitem').filter({ hasText: 'Result true' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'Display CHEMISTRY STUDIES' })
      ).toBeVisible();
      await expect(page.getByRole('listitem').filter({ hasText: 'Code 18719-5' })).toBeVisible();
      await expect(
        page.getByRole('listitem').filter({ hasText: 'System http://loinc.org' })
      ).toBeVisible();
    });
  });
});

test.describe('$expand', () => {
  test('type-level with filter', async ({ page }) => {
    await page.getByRole('button', { name: 'Operations' }).click();
    await page.getByRole('menuitem', { name: '$expand' }).click();

    await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();
    await expect(breadcrumbItem(page, '$expand')).toBeVisible();

    await page.getByRole('heading', { name: 'Parameters' }).click();
    await page
      .getByLabel('ValueSet URL')
      .fill('http://fhir.org/VCL?v1=(http://snomed.info/sct)concept<<119297000');
    await page.getByLabel('Filter').fill('blood');
    await page.getByRole('button', { name: 'Submit' }).click();

    await expect(page.getByText('Blood specimen with anticoagulant')).toBeVisible();
    await expect(page.getByText('Whole blood specimen with edetic acid')).toBeVisible();
    await expect(page.getByText('Thick blood smear specimen')).toBeVisible();

    // concepts not matching the filter should not appear
    await expect(page.getByText('p.m. serum specimen')).not.toBeVisible();
    await expect(page.getByText('Serum specimen from patient')).not.toBeVisible();
    await expect(page.getByText('Pooled platelet poor plasma specimen')).not.toBeVisible();
  });

  test('instance-level', async ({ page }) => {
    await page.goto(
      '/fhir/ValueSet?url=https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/mii-vs-labor-interpretation-eigenschaften-snomedct'
    );

    await page
      .getByRole('link', {
        name: 'MII VS Labor Interpretationsbeeinflussende Eigenschaften SNOMEDCT v2026.0.0'
      })
      .click();

    await expect(
      breadcrumbItem(
        page,
        'MII VS Labor Interpretationsbeeinflussende Eigenschaften SNOMEDCT v2026.0.0'
      )
    ).toBeVisible();
    await page.getByRole('button', { name: 'Operations' }).click();
    await page.getByRole('menuitem', { name: '$expand' }).click();

    await expect(breadcrumbItem(page, 'ValueSet')).toBeVisible();
    await expect(
      breadcrumbItem(
        page,
        'MII VS Labor Interpretationsbeeinflussende Eigenschaften SNOMEDCT v2026.0.0'
      )
    ).toBeVisible();
    await expect(breadcrumbItem(page, '$expand')).toBeVisible();

    await page.getByRole('button', { name: 'Submit' }).click();

    await expect(page.getByText('Specimen hemolyzed')).toBeVisible();
    await expect(page.getByText('Specimen lipemic')).toBeVisible();
    await expect(page.getByText('Specimen icteric')).toBeVisible();
    await expect(page.getByText('Urine culture - mixed growth')).toBeVisible();
  });

  test('instance-level with filter', async ({ page }) => {
    await page.goto(
      '/fhir/ValueSet?url=https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/mii-vs-labor-interpretation-eigenschaften-snomedct'
    );

    await page
      .getByRole('link', {
        name: 'MII VS Labor Interpretationsbeeinflussende Eigenschaften SNOMEDCT v2026.0.0'
      })
      .click();

    await expect(
      breadcrumbItem(
        page,
        'MII VS Labor Interpretationsbeeinflussende Eigenschaften SNOMEDCT v2026.0.0'
      )
    ).toBeVisible();
    await page.getByRole('button', { name: 'Operations' }).click();
    await page.getByRole('menuitem', { name: '$expand' }).click();

    await page.getByLabel('Filter').fill('lipemic');
    await page.getByRole('button', { name: 'Submit' }).click();

    await expect(page.getByText('Specimen lipemic')).toBeVisible();

    // concepts not matching the filter should not appear
    await expect(page.getByText('Specimen hemolyzed')).not.toBeVisible();
    await expect(page.getByText('Specimen icteric')).not.toBeVisible();
    await expect(page.getByText('Urine culture - mixed growth')).not.toBeVisible();
  });
});
