// Shared config for k6 scripts. All values come from environment variables so
// the same script runs locally and under CI with different targets/labels.

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080/portal-web').replace(/\/+$/, '');
export const VUS = parseInt(__ENV.VUS || '2', 10);
export const DURATION = __ENV.DURATION || '60s';
export const THINK_MS = parseInt(__ENV.THINK_MS || '500', 10);

// Run identity. Also passed on the CLI as --tag run_id=... --tag release=...,
// but kept here too so a bare `k6 run` still labels its metrics.
export const RUN_TAGS = {
  run_id: __ENV.RUN_ID || 'local',
  release: __ENV.RELEASE || 'unknown',
  test_type: __ENV.TEST_TYPE || 'ui',
};
