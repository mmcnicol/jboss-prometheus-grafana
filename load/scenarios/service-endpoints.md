# Scenario: service-endpoints ("middle" layer)

Driver: **k6 HTTP** (`load/k6/http-services/scenario.js`). Service endpoints have
real URLs and stable contracts, so k6's own metrics are the appropriate
client-side measure (Spike C). The services are also instrumented server-side
(`service_endpoint_seconds`), scraped during the run.

Per iteration:

| # | Call | k6 tag `name` | server route | notes |
|---|------|---------------|--------------|-------|
| 1 | `GET /service-a/api/patients?limit=20` | `GET /service-a/patients` | `/patients` | list |
| 2 | `GET /service-a/api/patients/PT-0xxxx` | `GET /service-a/patients/{ref}` | `/patients/{ref}` | ~4% deliberate 404 |
| 3 | `GET /service-b/api/codes` | `GET /service-b/codes` | `/codes` | reference data |
| 4 | `POST /service-b/api/validate` | `POST /service-b/validate` | `/validate` | ~30% deliberate 400 |

## Run it

```
./load/run-loadtest.sh --driver k6-http --vus 5 --duration 120 --release <x>
```
