package io.github.jpg.serviceb;

import io.github.jpg.metrics.jaxrs.EndpointTimingFilter;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.Set;

@ApplicationPath("/api")
public class RestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(CodeResource.class, ValidationResource.class, EndpointTimingFilter.class);
    }
}
