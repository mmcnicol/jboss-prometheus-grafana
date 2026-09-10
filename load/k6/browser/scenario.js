// k6 browser driver for the happy-path scenario (load/scenarios/happy-path.md).
//
// Alternative UI driver + client-side cross-check (decision D6): the server-side
// per-action timer is still the source of truth; this also records the
// CLIENT-observed time for each step as `client_action_seconds{action=...}` so
// Grafana can overlay client vs server latency.
//
//   K6_BROWSER_EXECUTABLE_PATH=/usr/bin/chromium \
//   K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//   BASE_URL=http://localhost:8080/portal-web VUS=2 DURATION=60s \
//   k6 run -o experimental-prometheus-rw \
//     --tag run_id=$RUN_ID --tag release=$RELEASE load/k6/browser/scenario.js

import { browser } from 'k6/browser';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, VUS, DURATION, THINK_MS } from '../lib/config.js';

const clientAction = new Trend('client_action_seconds', false);

export const options = {
  scenarios: {
    ui: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      options: { browser: { type: 'chromium' } },
    },
  },
  thresholds: {
    checks: ['rate>0.95'],
  },
};

// JSF client ids contain ':', which must be escaped in a CSS selector.
const byId = (id) => `#${id.replace(/:/g, '\\:')}`;

async function timed(action, fn) {
  const start = Date.now();
  await fn();
  clientAction.add((Date.now() - start) / 1000, { action });
}

export default async function () {
  const page = await browser.newPage();
  try {
    await timed('login', async () => {
      await page.goto(`${BASE_URL}/login.xhtml`);
      await page.locator(byId('loginForm:username')).type('perf-user');
      await page.locator(byId('loginForm:password')).type('test');
      await Promise.all([
        page.waitForNavigation(),
        page.locator(byId('loginForm:loginButton')).click(),
      ]);
    });
    check(page, { 'on discharge list': (p) => p.url().includes('/secure/discharges.xhtml') });

    sleep(THINK_MS / 1000);

    await timed('discharge.view', async () => {
      await page.goto(`${BASE_URL}/secure/discharge.xhtml`);
      await page.locator(byId('dischargeForm:patientReference')).waitFor();
    });

    sleep(THINK_MS / 1000);

    await timed('discharge.save', async () => {
      const ref = `PT-${Math.random().toString(16).slice(2, 10).toUpperCase()}`;
      await page.locator(byId('dischargeForm:patientReference')).type(ref);
      await page.locator(byId('dischargeForm:ward')).type('Ward A');
      await page.locator(byId('dischargeForm:summary')).type('k6-browser generated discharge.');
      await Promise.all([
        page.waitForNavigation(),
        page.locator(byId('dischargeForm:saveButton')).click(),
      ]);
    });
    check(page, { 'saved -> list': (p) => p.url().includes('/secure/discharges.xhtml') });

    sleep(THINK_MS / 1000);
  } finally {
    await page.close();
  }
}
