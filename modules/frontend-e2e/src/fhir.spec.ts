import type {Page} from '@playwright/test';
import {expect, test} from '@playwright/test';

test.beforeEach('Sign In', async ({page}) => {
    await page.goto('/fhir');

    // Blaze Sign-In Page
    await expect(page).toHaveTitle("Sign-In - Blaze");
    await page.getByRole('button', {name: 'Sign in with Keycloak'}).click()

    // Keycloak Sign-In Page
    await expect(page).toHaveTitle("Sign in to Keycloak");
    await page.getByLabel('Username or email').fill('john')
    await page.getByLabel('Password', {exact: true}).fill('insecure')
    await page.getByRole('button', {name: 'Sign In'}).click()

    await expect(page).toHaveTitle("Home - Blaze");
});

async function expectActiveNavLink(page: Page, name: string): Promise<void> {
    const overviewLink = page.getByRole('link', {name: name});
    await expect(overviewLink).toBeVisible();
    await expect(overviewLink).toHaveCSS('border-bottom-color', 'rgb(99, 102, 241)');
    await expect(overviewLink).toHaveAttribute('aria-current', 'page');
}

async function expectInActiveNavLink(page: Page, name: string): Promise<void> {
    const overviewLink = page.getByRole('link', {name: name});
    await expect(overviewLink).toBeVisible();
    await expect(overviewLink).toHaveCSS('border-bottom-color', 'rgba(0, 0, 0, 0)');
    await expect(overviewLink).not.toHaveAttribute('aria-current', 'page');
}

test('Home Page', async ({page}) => {
    await expect(page).toHaveTitle("Home - Blaze");
    await expect(page.getByRole('heading', {name: 'Resource Types'})).toBeVisible();

    await expect(page.getByRole('link', {name: 'AllergyIntolerance'})).toBeVisible();
    await expect(page.getByRole('link', {name: 'CarePlan'})).toBeVisible();
});

test('History Page', async ({page}) => {
    await page.getByRole('link', {name: 'History', exact: true}).click();

    await expect(page).toHaveTitle("History - Blaze");
    await expect(page.getByText('Total: 92,114')).toBeVisible();
});

test('Metadata Page', async ({page}) => {
    await page.getByRole('link', {name: 'Metadata', exact: true}).click();

    await expect(page).toHaveTitle("Metadata - Blaze");
    await expectActiveNavLink(page, 'Interactions');
    await expectInActiveNavLink(page, 'Json');

    await expect(page.getByRole('heading', {name: 'Blaze'})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Account'})).toBeVisible();
    await expect(page.getByRole('link', {name: 'ActivityDefinition'})).toBeVisible();

});

test.describe('Admin', () => {

    test('Overview', async ({page}) => {
        await page.getByRole('link', {name: 'Admin', exact: true}).click();

        await expect(page).toHaveTitle("Overview - Admin - Blaze");
        await expectActiveNavLink(page, 'Overview');
        await expectInActiveNavLink(page, 'Databases');

        await expect(page.getByRole('heading', {name: 'Settings'})).toBeVisible();
        await expect(page.getByRole('heading', {name: 'Features'})).toBeVisible();
    });

    test('Databases', async ({page}) => {
        await page.getByRole('link', {name: 'Admin', exact: true}).click();
        await page.getByRole('link', {name: 'Databases', exact: true}).click();

        await expect(page).toHaveTitle("Databases - Admin - Blaze");
        await expectInActiveNavLink(page, 'Overview');
        await expectActiveNavLink(page, 'Databases');

        await expect(page.getByRole('link', {name: 'Index'})).toBeVisible();
        await expect(page.getByRole('link', {name: 'Transaction'})).toBeVisible();
        await expect(page.getByRole('link', {name: 'Resource'})).toBeVisible();
    });

    test.describe('Databases', () => {

        test('Index', async ({page}) => {
            await page.getByRole('link', {name: 'Admin', exact: true}).click();
            await page.getByRole('link', {name: 'Databases', exact: true}).click();
            await page.getByRole('link', {name: 'Index', exact: true}).click();

            await expect(page).toHaveTitle("Index Database - Admin - Blaze");
            await expectInActiveNavLink(page, 'Overview');
            await expectActiveNavLink(page, 'Databases');

            await expect(page.getByRole('heading', {name: 'Index'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Column Families'})).toBeVisible();

            await expect(page.getByRole('link', {name: 'ResourceAsOfIndex'})).toBeVisible();
        });

        test.describe('Index', () => {

            test('ResourceAsOfIndex Column Family', async ({page}) => {
                await page.getByRole('link', {name: 'Admin', exact: true}).click();
                await page.getByRole('link', {name: 'Databases', exact: true}).click();
                await page.getByRole('link', {name: 'Index', exact: true}).click();
                await page.getByRole('link', {name: 'ResourceAsOfIndex', exact: true}).click();

                await expect(page).toHaveTitle("ResourceAsOfIndex Column Family - Index Database - Admin - Blaze");
                await expectInActiveNavLink(page, 'Overview');
                await expectActiveNavLink(page, 'Databases');

                await expect(page.getByRole('heading', {name: 'Index - ResourceAsOfIndex'})).toBeVisible();
                await expect(page.getByRole('heading', {name: 'Levels'})).toBeVisible();
            });
        });
    });
});

test('Patients Page', async ({page}) => {
    await page.getByRole('link', {name: 'Patient'}).click();

    await expect(page).toHaveTitle("Patient - Blaze");
    await expect(page.getByText('Total: 120')).toBeVisible();

    // Go to next page
    await page.getByRole('link', {name: 'Next'}).first().click();
    await expect(page.getByText('Non-First Page')).toBeVisible();
});

test('Patients History Page', async ({page}) => {
    await page.getByRole('link', {name: 'Patient'}).click();
    await expect(page).toHaveTitle("Patient - Blaze");
    await page.getByTitle('Patient History').click()

    await expect(page).toHaveTitle("History - Patient - Blaze");
    await expect(page.getByText('Total: 120')).toBeVisible();
});

test('Signing in after sign out goes to the Keycloak Sign-In Page', async ({page}) => {
    await page.getByRole('button', {name: 'Open user menu'}).click();
    await page.getByRole('menuitem', {name: 'Sign out'}).click();

    // Blaze Sign-In Page
    await expect(page).toHaveTitle("Sign-In - Blaze");
    await page.getByRole('button', {name: 'Sign in with Keycloak'}).click()

    // Keycloak Sign-In Page
    await expect(page).toHaveTitle("Sign in to Keycloak");
});
