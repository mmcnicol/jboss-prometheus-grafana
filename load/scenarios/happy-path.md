# Scenario: happy-path

Driver-agnostic definition. Any driver (Selenium/Java — primary, k6 browser,
k6 HTTP) performs these steps; the per-action timing is recorded server-side by
the application.

| # | Step | Action name (server-side) | Notes |
|---|------|---------------------------|-------|
| 1 | Open `/login.xhtml`, enter username + password `test`, click **Sign in** | `login` | resolved from `javax.faces.source = loginForm:loginButton` |
| 2 | Navigate to `/secure/discharge.xhtml` | `discharge.view` | full-page GET, resolved from view id |
| 3 | Fill patient reference, ward, summary; click **Save discharge** | `discharge.save` | resolved from `javax.faces.source = dischargeForm:saveButton` |
| 4 | Land on `/secure/discharges.xhtml` (list) | `discharge.list` | full-page GET after redirect |

Think time: ~500 ms between steps (configurable).

Loop: repeat steps 1–4 for the configured duration, per virtual user.

## Run it (Selenium/Java)

```
mvn -q -pl load/selenium-java -am compile
mvn -q -pl load/selenium-java exec:java \
  -DbaseUrl=http://localhost:8080/portal-web -Dvus=2 -DdurationSeconds=60
```
