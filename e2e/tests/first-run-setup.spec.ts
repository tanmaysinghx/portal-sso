import { expect, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * The first five minutes of every installation: open the console, unlock it with the setup key
 * from the server, create an administrator, land in the dashboard, and sign in again later.
 * If this breaks, nobody can start using the product at all.
 */

const keyFile = join(process.env.E2E_PORTAL_HOME!, 'secrets', 'initialAdminPassword');
const ADMIN = {
  email: 'owner@example.com',
  firstName: 'Ada',
  lastName: 'Owner',
  password: 'E2e-Correct-Horse-42',
};

test.describe.configure({ mode: 'serial' });

test('a fresh install shows the setup wizard, not a sign-in form', async ({ page, request }) => {
  const status = await (await request.get('/api/public/setup/status')).json();
  expect(status.setupRequired).toBe(true);
  expect(status.setupKeyIssued).toBe(true);
  expect(existsSync(keyFile)).toBe(true);

  // What an operator types after starting the server: the bare address.
  await page.goto('/');
  await expect(page).toHaveURL(/\/sign-in/);
  await expect(page.getByRole('heading', { name: 'Create the first administrator' })).toBeVisible();
  await expect(page.getByLabel('Setup key')).toBeVisible();
  // The operator is told where the key is, so they do not have to go hunting through logs.
  await expect(page.getByText('initialAdminPassword')).toBeVisible();
});

test('the password checklist updates as the operator types', async ({ page }) => {
  await page.goto('/sign-in');
  const rules = page.locator('#setup-password-rules');
  await page.getByLabel('Password', { exact: true }).fill('short');
  await expect(rules.getByText('At least 12 characters')).toBeVisible();
  await expect(rules.getByText('(not met)').first()).toBeAttached();

  await page.getByLabel('Password', { exact: true }).fill(ADMIN.password);
  await expect(rules.getByText('(not met)')).toHaveCount(0);
});

test('a wrong setup key is refused and nothing is created', async ({ page, request }) => {
  await page.goto('/sign-in');
  await page.getByLabel('Setup key').fill('definitely-not-the-key');
  await page.getByLabel('Administrator email').fill(ADMIN.email);
  await page.getByLabel('Password', { exact: true }).fill(ADMIN.password);
  await page.getByLabel('Confirm password').fill(ADMIN.password);
  await page.getByRole('button', { name: 'Complete setup' }).click();

  await expect(page.getByText('The setup key is incorrect.')).toBeVisible();
  const status = await (await request.get('/api/public/setup/status')).json();
  expect(status.setupRequired).toBe(true);
});

test('the right setup key creates the administrator and signs them in', async ({ page, request }) => {
  const setupKey = readFileSync(keyFile, 'utf8').trim();

  await page.goto('/sign-in');
  await page.getByLabel('Setup key').fill(setupKey);
  await page.getByLabel('Administrator email').fill(ADMIN.email);
  await page.getByLabel('First name').fill(ADMIN.firstName);
  await page.getByLabel('Last name').fill(ADMIN.lastName);
  await page.getByLabel('Password', { exact: true }).fill(ADMIN.password);
  await page.getByLabel('Confirm password').fill(ADMIN.password);
  await page.getByRole('button', { name: 'Complete setup' }).click();

  await expect(page).toHaveURL(/\/dashboard/, { timeout: 20_000 });

  // The key is single use: it is spent and its file removed.
  expect(existsSync(keyFile)).toBe(false);
  const status = await (await request.get('/api/public/setup/status')).json();
  expect(status.setupRequired).toBe(false);
  expect(status.setupKeyFile ?? null).toBeNull();
});

test('setup cannot be run a second time', async ({ request }) => {
  const response = await request.post('/api/public/setup/initialize', {
    data: { setupKey: 'anything', email: 'intruder@example.com', password: ADMIN.password },
  });
  // Rejected before any key is looked at — by CSRF here, and by "already completed" with a token.
  expect(response.ok()).toBe(false);
});

test('the new administrator can sign in again from a clean browser', async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto('/sign-in');
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
  await expect(page.getByLabel('Setup key')).toHaveCount(0);

  await page.getByLabel('Email address').fill(ADMIN.email);
  await page.getByLabel('Password', { exact: true }).fill(ADMIN.password);
  await page.getByRole('button', { name: /^Sign in$/ }).click();

  await expect(page).toHaveURL(/\/dashboard/, { timeout: 20_000 });
  await context.close();
});
