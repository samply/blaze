import { expect, test } from '@playwright/test';

test('the home page is shown without sign-in', async ({ page }) => {
  await page.goto('/fhir');

  await expect(page).toHaveTitle('Home - Blaze');
});

test('the navigation has no admin section and no user menu', async ({ page }) => {
  await page.goto('/fhir');

  const navigation = page.getByRole('navigation');
  await expect(navigation.getByRole('link', { name: 'Home' })).toBeVisible();
  await expect(navigation.getByRole('link', { name: 'History' })).toBeVisible();
  await expect(navigation.getByRole('link', { name: 'Metadata' })).toBeVisible();
  await expect(navigation.getByRole('link', { name: 'Admin' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Open user menu' })).toHaveCount(0);
});

// A client-side navigation issues a `__data.json` request, whose load fetches
// from the backend without an access token.
test('an in-app navigation shows the resources', async ({ page }) => {
  await page.goto('/fhir');

  await page.getByRole('link', { name: 'Patient' }).click();

  await expect(page).toHaveTitle('Patient - Blaze');
});

for (const path of [
  '/fhir/__admin',
  '/fhir/__admin/jobs',
  '/fhir/__admin/dbs',
  '/fhir/__sign-in'
]) {
  test(`${path} is not found`, async ({ page }) => {
    const response = await page.goto(path);

    expect(response?.status()).toBe(404);
  });
}

// Form actions run before any load, so the admin layout alone wouldn't stop
// them.
test('an admin form action is not found', async ({ page, baseURL }) => {
  const response = await page.request.post('/fhir/__admin/jobs/new/re-index', {
    headers: { origin: baseURL! },
    form: { 'search-param-url': 'http://hl7.org/fhir/SearchParameter/Resource-profile' }
  });

  expect(response.status()).toBe(404);
});

// Without login, API requests carry no access token, so the ingress tells
// them apart from the requests of browsers by the Accept header.
for (const [accept, contentType] of [
  ['application/fhir+json', 'application/fhir+json;charset=utf-8'],
  ['application/fhir+xml', 'application/fhir+xml;charset=utf-8']
]) {
  test(`a FHIR request with Accept ${accept} reaches the backend`, async ({ request }) => {
    const response = await request.get('/fhir/Patient?_summary=count', {
      headers: { Accept: accept }
    });

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toBe(contentType);
  });
}

test('a request with Accept application/json reaches the frontend', async ({ request }) => {
  const response = await request.get('/fhir/Patient', { headers: { Accept: 'application/json' } });

  expect(response.headers()['content-type']).toContain('text/html');
});
