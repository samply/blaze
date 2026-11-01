import { defineConfig } from '@playwright/test';
import config from './playwright.config';

/**
 * The tests of the frontend deployed without login (`ENABLE_AUTH=false`),
 * running against `docker-compose-no-auth.yml`.
 */
export default defineConfig({
  ...config,
  testDir: './src/no-auth',
  testIgnore: undefined
});
