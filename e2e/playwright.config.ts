import { defineConfig, devices } from '@playwright/test';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

/**
 * Boots the real packaged JAR against an empty home directory, so every run is a brand-new install:
 * fresh H2 database, no administrator, and a setup key written to <home>/secrets.
 *
 *   E2E_JAR          the JAR to run (default ../portal-server/target/portal-sso.jar)
 *   E2E_PORT         port to serve on (default 18080)
 *   E2E_PORTAL_HOME  home directory; a new temp dir when unset
 *
 * Set in the runner before workers fork, so every worker sees the same home.
 */
process.env.E2E_PORTAL_HOME ??= mkdtempSync(join(tmpdir(), 'portal-sso-e2e-'));

const port = Number(process.env.E2E_PORT ?? 18080);
const baseURL = `http://localhost:${port}`;
const jar = resolve(process.env.E2E_JAR ?? '../portal-server/target/portal-sso.jar');

export default defineConfig({
  testDir: './tests',
  // One server, one database, and tests that build on each other's state (setup happens once).
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 60_000,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `java -jar "${jar}" --server.port=${port}`,
    url: `${baseURL}/api/public/setup/status`,
    timeout: 180_000,
    reuseExistingServer: false,
    stdout: 'pipe',
    stderr: 'pipe',
    env: {
      PORTAL_HOME: process.env.E2E_PORTAL_HOME,
      ISSUER_URL: baseURL,
      SERVER_SERVLET_SESSION_COOKIE_SECURE: 'false',
      APP_BOOTSTRAP_AUTO_GENERATE: 'true',
      APP_BOOTSTRAP_ADMIN_EMAIL: '',
      APP_BOOTSTRAP_ADMIN_PASSWORD: '',
    },
  },
});
