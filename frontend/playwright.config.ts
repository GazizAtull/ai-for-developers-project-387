import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

const backendDir = path.resolve(import.meta.dirname, '../backend');

// Браузерный сценарий против настоящего бэкенда: Spring на :8080, Vite на :5173 проксирует /api.
// Ответы API не подменяются.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'list' : 'line',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: process.platform === 'win32' ? `"${path.join(backendDir, 'gradlew.bat')}" --no-daemon bootRun` : './gradlew --no-daemon bootRun',
      cwd: backendDir,
      url: 'http://localhost:8080/api/event-types',
      timeout: 240_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
