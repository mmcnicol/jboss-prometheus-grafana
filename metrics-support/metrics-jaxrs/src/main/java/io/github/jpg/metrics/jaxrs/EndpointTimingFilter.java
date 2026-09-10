package io.github.jpg.metrics.jaxrs;

import io.github.jpg.metrics.Metrics;

import javax.servlet.ServletContext;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Times every JAX-RS request and records
 * {@code service_endpoint_seconds{service, route, method, status, outcome}}.
 *
 * <p>{@code route} is the matched path template built from the resource class and
 * method {@code @Path} annotations (e.g. {@code /patients/{ref}}), so ids and
 * other path parameters do not explode label cardinality.
 *
 * <p>{@code service} comes from the {@code metrics.service.name} servlet context
 * init-param, falling back to the context path. Never throws into the request
 * (requirement NFR2).
 */
@Provider
public class EndpointTimingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(EndpointTimingFilter.class.getName());
    private static final String START_NANOS = "io.github.jpg.metrics.jaxrs.start";

    @Context
    private ServletContext servletContext;

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext request) {
        request.setProperty(START_NANOS, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        try {
            Object start = request.getProperty(START_NANOS);
            if (!(start instanceof Long)) {
                return;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - (Long) start);
            Metrics.get()
                    .endpoint(serviceName(), route(), request.getMethod())
                    .record(elapsed, response.getStatus());
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "endpoint timing skipped", e);
        }
    }

    private String serviceName() {
        if (servletContext != null) {
            String configured = servletContext.getInitParameter("metrics.service.name");
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
            String ctx = servletContext.getContextPath();
            if (ctx != null && ctx.length() > 1) {
                return ctx.substring(1);
            }
        }
        return "service";
    }

    private String route() {
        if (resourceInfo == null) {
            return "unknown";
        }
        Class<?> clazz = resourceInfo.getResourceClass();
        Method method = resourceInfo.getResourceMethod();
        String classPath = clazz != null ? pathValue(clazz.getAnnotation(Path.class)) : "";
        String methodPath = method != null ? pathValue(method.getAnnotation(Path.class)) : "";
        return normalize(classPath + "/" + methodPath);
    }

    private static String pathValue(Path p) {
        return p == null ? "" : p.value();
    }

    static String normalize(String raw) {
        String s = ("/" + raw).replaceAll("/{2,}", "/");
        if (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "/" : s;
    }
}
