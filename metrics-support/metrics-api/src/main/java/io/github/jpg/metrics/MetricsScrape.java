package io.github.jpg.metrics;

import java.nio.charset.StandardCharsets;

/**
 * A rendered metrics exposition, ready to write to an HTTP response.
 *
 * <p>The {@code /metrics} endpoint in the web module asks {@link Metrics#scrape()}
 * for this; when metrics are disabled the call returns empty and the endpoint
 * responds 404 (requirement FR1/FR3).
 */
public final class MetricsScrape {

    private final String contentType;
    private final byte[] body;

    public MetricsScrape(String contentType, String body) {
        this.contentType = contentType;
        this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    /** e.g. {@code text/plain; version=0.0.4; charset=utf-8}. */
    public String contentType() {
        return contentType;
    }

    /** UTF-8 bytes of the exposition. */
    public byte[] body() {
        return body.clone();
    }
}
