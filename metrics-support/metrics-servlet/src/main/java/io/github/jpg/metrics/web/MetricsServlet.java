package io.github.jpg.metrics.web;

import io.github.jpg.metrics.Metrics;
import io.github.jpg.metrics.MetricsScrape;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * Prometheus scrape endpoint at {@code <context>/metrics}.
 *
 * <p>Served only when {@code portal.metrics.enabled=true} and a backend is
 * present; otherwise {@link Metrics#scrape()} is empty and this responds 404
 * (requirement FR1/FR3). The OpenTelemetry Collector — started only for the
 * duration of a load-test run — is what scrapes this.
 *
 * <p>Not exposed publicly in a real deployment (requirement NFR7): bind it to an
 * internal interface / network.
 */
@WebServlet(urlPatterns = "/metrics", name = "metrics")
public class MetricsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<MetricsScrape> scrape = Metrics.get().scrape();
        if (!scrape.isPresent()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "metrics disabled");
            return;
        }
        MetricsScrape s = scrape.get();
        byte[] body = s.body();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(s.contentType());
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }
}
