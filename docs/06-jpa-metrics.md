# JPA (Hibernate) metrics for load testing

**Question.** If a Java EE web app on JBoss/WildFly uses JPA, what could a
load-test dashboard show?

**Answer.** Quite a lot, and still with no application code. WildFly publishes
Hibernate's statistics for each persistence unit through its management model,
and they should appear on the same `:9990/metrics` endpoint the dashboard
already scrapes (Spike E).

**Status: not verified.** The demo app has no JPA, and none of these metrics
has been seen on a running server. Expected names follow the
`wildfly_jpa_<attribute>` pattern, with `deployment` and persistence-unit
labels. Confirm with `curl -s localhost:9990/metrics | grep wildfly_jpa` before
building panels. See also [backlog](05-backlog.md) item 6.

## Turning statistics on

Hibernate statistics are off by default because they add a small overhead.
There are two ways to enable them:

- **In the app.** Set `hibernate.generate_statistics=true` in
  `persistence.xml`. This is a change to the app.
- **At runtime, via `jboss-cli`.** It fits the "on only for a load test"
  approach used elsewhere, and could be added to
  `scripts/on-vm-enable-statistics.sh`:

  ```
  /deployment=myapp.war/subsystem=jpa/hibernate-persistence-unit=myapp.war#myPU:write-attribute(name=statistics-enabled,value=true)
  ```

  The setting is lost on redeploy, so run it after deploying.

## What is worth showing

Most useful first:

| What | Why it matters under load |
|---|---|
| SQL statements per request (prepared-statement count ÷ request count) | The best single N+1 query detector. If this number goes up between releases, someone added a lazy-loading loop. |
| Entity and collection fetch counts | The same N+1 signal from another angle: lazy loads happening one row at a time. |
| Slowest query time | Shows the worst query during the run. Hibernate also records the query text, but Prometheus can't hold text, so read it with `jboss-cli`. |
| Second-level and query cache hit ratio | Only relevant if caching is on. A falling hit ratio under load means cache eviction or a cache that's too small. |
| Optimistic lock failures | Concurrent users updating the same rows. Rarely shows up in functional tests, often shows up in load tests. |
| Sessions opened − closed | If the gap keeps growing, EntityManagers are leaking. |
| Flush count per transaction | Unexpected flushes, often caused by queries run mid-transaction, are a hidden cost. |

## What to leave out

Hibernate also keeps statistics for each entity and each query. Exported as
metric labels, they would create a large number of series. Check them with
`jboss-cli` when investigating, not on the dashboard.

## Seeing it in the demo

The demo can't show any of this yet because it has no JPA. Backlog item 6
(real `ExampleDS` traffic) lists "a single JPA entity" as one option.
Choosing JPA there would give these panels something to show.
