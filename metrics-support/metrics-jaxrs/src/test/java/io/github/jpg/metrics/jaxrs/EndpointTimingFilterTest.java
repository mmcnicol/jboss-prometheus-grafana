package io.github.jpg.metrics.jaxrs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndpointTimingFilterTest {

    @Test
    void normalizeJoinsClassAndMethodPaths() {
        assertEquals("/patients/{ref}", EndpointTimingFilter.normalize("patients/{ref}"));
        assertEquals("/patients", EndpointTimingFilter.normalize("patients/"));
        assertEquals("/patients", EndpointTimingFilter.normalize("/patients"));
        assertEquals("/codes/{code}/detail", EndpointTimingFilter.normalize("codes//{code}//detail"));
        assertEquals("/", EndpointTimingFilter.normalize(""));
        assertEquals("/", EndpointTimingFilter.normalize("/"));
    }
}
