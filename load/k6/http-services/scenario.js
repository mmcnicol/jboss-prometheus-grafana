// k6 HTTP driver for the "middle" load-test layer — service-a and service-b
// endpoints hit directly. These have real URLs and stable contracts, so k6's
// own metrics (k6_http_req_duration{name=...}) are the appropriate measure
// (Spike C). The services are also instrumented server-side
// (service_endpoint_seconds), scraped by the OTel Collector during the run.
//
//   K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//   BASE=http://localhost:8080 VUS=5 DURATION=60s \
//   k6 run -o experimental-prometheus-rw \
//     --tag run_id=$RUN_ID --tag release=$RELEASE --tag test_type=service \
//     load/k6/http-services/scenario.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = (__ENV.BASE || __ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const options = {
  scenarios: {
    services: {
      executor: 'constant-vus',
      vus: parseInt(__ENV.VUS || '5', 10),
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: {
    checks: ['rate>0.95'],
    'http_req_duration{expected_response:true}': ['p(95)<500'],
  },
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json', Accept: 'application/json' } };

export default function () {
  // service-a: list patients
  let res = http.get(`${BASE}/service-a/api/patients?limit=20`, { tags: { name: 'GET /service-a/patients' } });
  check(res, { 'patients 200': (r) => r.status === 200 });

  // service-a: one patient (mix of hits and a deliberate 404)
  const id = 1000 + Math.floor(Math.random() * 520); // ~4% miss
  res = http.get(`${BASE}/service-a/api/patients/PT-${String(id).padStart(5, '0')}`,
    { tags: { name: 'GET /service-a/patients/{ref}' } });
  check(res, { 'patient 200 or 404': (r) => r.status === 200 || r.status === 404 });

  sleep(0.2);

  // service-b: list codes
  res = http.get(`${BASE}/service-b/api/codes`, { tags: { name: 'GET /service-b/codes' } });
  check(res, { 'codes 200': (r) => r.status === 200 });

  // service-b: validate (mix of valid and invalid)
  const valid = Math.random() > 0.3;
  const payload = valid
    ? { patientReference: `PT-0${id}`, dischargeCode: 'DC01' }
    : { dischargeCode: 'NOPE' };
  res = http.post(`${BASE}/service-b/api/validate`, JSON.stringify(payload),
    { ...JSON_HEADERS, tags: { name: 'POST /service-b/validate' } });
  check(res, { 'validate 200 or 400': (r) => r.status === 200 || r.status === 400 });

  sleep(0.3);
}
