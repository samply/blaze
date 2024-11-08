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

    const goToAdmin = async (page: Page) => {
        await page.getByRole('link', {name: 'Admin', exact: true}).click();
    }

    test('Overview', async ({page}) => {
        await goToAdmin(page);

        await expect(page).toHaveTitle("Overview - Admin - Blaze");
        await expectActiveNavLink(page, 'Overview');
        await expectInActiveNavLink(page, 'Databases');

        await expect(page.getByRole('heading', {name: 'Settings'})).toBeVisible();
        await expect(page.getByRole('heading', {name: 'Features'})).toBeVisible();
    });

    test.describe('Databases', () => {

        const goToDatabases = async (page: Page) => {
            await goToAdmin(page);
            await page.getByRole('link', {name: 'Databases', exact: true}).click();
        }

        test('Overview', async ({page}) => {
            await goToDatabases(page);

            await expect(page).toHaveTitle("Databases - Admin - Blaze");
            await expectInActiveNavLink(page, 'Overview');
            await expectActiveNavLink(page, 'Databases');

            await expect(page.getByRole('link', {name: 'Index'})).toBeVisible();
            await expect(page.getByRole('link', {name: 'Transaction'})).toBeVisible();
            await expect(page.getByRole('link', {name: 'Resource'})).toBeVisible();
        });

        test.describe('Index', () => {

            const goToIndexDatabase = async (page: Page) => {
                await goToDatabases(page);
                await page.getByRole('link', {name: 'Index', exact: true}).click();
            }

            test('Overview', async ({page}) => {
                await goToIndexDatabase(page);

                await expect(page).toHaveTitle("Index Database - Admin - Blaze");
                await expectInActiveNavLink(page, 'Overview');
                await expectActiveNavLink(page, 'Databases');

                await expect(page.getByRole('heading', {name: 'Index'})).toBeVisible();
                await expect(page.locator("dl").getByText('File System Usage')).toBeVisible();
                await expect(page.locator("dl").getByText('Usable Space')).toBeVisible();
                await expect(page.locator("dl").getByText('Block Cache Usage')).toBeVisible();
                await expect(page.locator("dl").getByText('Compactions')).toBeVisible();

                await expect(page.getByRole('heading', {name: 'Column Families'})).toBeVisible();
                await expect(page.getByRole('columnheader', { name: 'Name' })).toBeVisible();
                await expect(page.getByRole('columnheader', { name: '# Keys' })).toBeVisible();
                await expect(page.getByRole('columnheader', { name: 'File Size' })).toBeVisible();
                await expect(page.getByRole('columnheader', { name: 'Memtable Size' })).toBeVisible();

                for (const name of ['ResourceAsOfIndex', 'ResourceValueIndex', 'SearchParamValueIndex']) {
                    await expect(page.getByRole('link', {name: name, exact: true})).toBeVisible();
                }
            });

            test('ResourceAsOfIndex Column Family', async ({page}) => {
                await goToIndexDatabase(page);
                await page.getByRole('link', {name: 'ResourceAsOfIndex', exact: true}).click();

                await expect(page).toHaveTitle("ResourceAsOfIndex Column Family - Index Database - Admin - Blaze");
                await expectInActiveNavLink(page, 'Overview');
                await expectActiveNavLink(page, 'Databases');

                await expect(page.getByRole('heading', {name: 'Index - ResourceAsOfIndex'})).toBeVisible();
                await expect(page.locator("dl").getByText('File System Usage')).toBeVisible();
                await expect(page.locator("dl").getByText('# Files')).toBeVisible();

                await expect(page.getByRole('heading', {name: 'Levels'})).toBeVisible();
                await expect(page.getByRole('columnheader', { name: 'Level' })).toBeVisible();
                await expect(page.getByRole('columnheader', { name: '# Files' })).toBeVisible();
                await expect(page.getByRole('columnheader', { name: 'Size' })).toBeVisible();
            });
        });
    });

    test.describe('Jobs', () => {

        const goToJobs = async (page: Page) => {
            await goToAdmin(page);
            await page.getByRole('link', {name: 'Jobs', exact: true}).click();
        }

        test('Overview', async ({page}) => {
            await goToJobs(page);

            await expect(page).toHaveTitle("Jobs - Admin - Blaze");
            await expect(page.getByText('All Jobs')).toBeVisible();
            await expect(page.getByRole('link', {name: 'New Job'})).toBeVisible();
        });

        test('Create Compact a Database Column Family Job and wait unit completed', async ({page}) => {
            await goToJobs(page);
            await page.getByRole('link', {name: 'New Job', exact: true}).click();

            await expect(page).toHaveTitle("Create New Job - Admin - Blaze");
            await page.getByRole('link', {name: 'Compact a Database Column Family'}).click()

            const database = 'Index';
            const columnFamily = 'ResourceAsOfIndex';

            await page.getByLabel('Database').selectOption(database)
            await page.getByLabel('Column Family').selectOption(columnFamily)
            await page.getByRole('button', {name: 'Submit New Job'}).click()

            await expect(page).toHaveTitle(/Job #\d+ - Admin - Blaze/);
            await expect(page.getByRole('heading', {name: /Job #\d+/})).toBeVisible();
            await expect(page.getByText('Status')).toBeVisible();
            await expect(page.getByText('Compact a Database Column Family')).toBeVisible();
            await expect(page.getByText('Database ' + database)).toBeVisible();
            await expect(page.getByText('Column Family ' + columnFamily)).toBeVisible();

            // may appear later
            await expect(page.getByText('Processing Duration')).toBeVisible({timeout: 50000});
            await expect(page.getByText('Status completed')).toBeVisible({timeout: 50000});
        });

        test('Create (Re)Index a Search Parameter Job and wait unit completed', async ({page}) => {
            await goToJobs(page);
            await page.getByRole('link', {name: 'New Job', exact: true}).click();

            await expect(page).toHaveTitle("Create New Job - Admin - Blaze");
            await page.getByRole('link', {name: '(Re)Index a Search Parameter'}).click()

            await expect(page).toHaveTitle("Create New (Re)Index a Search Parameter Job - Admin - Blaze");

            const searchParamUrl = 'http://hl7.org/fhir/SearchParameter/Resource-profile';

            await page.getByLabel('Search Param URL').fill(searchParamUrl)
            await page.getByRole('button', {name: 'Submit New Job'}).click()

            await expect(page).toHaveTitle(/Job #\d+ - Admin - Blaze/);
            await expect(page.getByRole('heading', {name: /Job #\d+/})).toBeVisible();
            await expect(page.getByText('Status')).toBeVisible();
            await expect(page.getByText('Type (Re)Index a Search')).toBeVisible();
            await expect(page.getByText('Search Param URL ' + searchParamUrl)).toBeVisible();

            // may appear later
            await expect(page.getByText('Total Resources 92.1 k')).toBeVisible({timeout: 30000});
            await expect(page.getByText('Resources Processed 92.1 k')).toBeVisible({timeout: 50000});
            await expect(page.getByText('Processing Duration')).toBeVisible({timeout: 50000});
            await expect(page.getByText('Status completed')).toBeVisible({timeout: 50000});
        });

        test('Cancel Job Creation', async ({page}) => {
            await goToJobs(page);
            await page.getByRole('link', {name: 'New Job', exact: true}).click();

            await expect(page).toHaveTitle("Create New Job - Admin - Blaze");
            await page.getByRole('link', {name: '(Re)Index a Search Parameter'}).click()

            await expect(page).toHaveTitle("Create New (Re)Index a Search Parameter Job - Admin - Blaze");
            await page.getByRole('link', {name: 'Cancel'}).click()

            await expect(page).toHaveTitle("Jobs - Admin - Blaze");
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
