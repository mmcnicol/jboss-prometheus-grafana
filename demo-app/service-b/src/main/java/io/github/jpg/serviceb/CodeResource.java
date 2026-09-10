package io.github.jpg.serviceb;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Thin JAX-RS wrapper over {@link CodeRegistry}. Not unit-tested (glue). */
@Path("codes")
@Produces(MediaType.APPLICATION_JSON)
public class CodeResource {

    private final CodeRegistry registry = new CodeRegistry();

    @GET
    public Map<String, String> all() {
        sleep(5, 4);
        return registry.all();
    }

    @GET
    @Path("{code}")
    public Map<String, Object> one(@PathParam("code") String code) {
        sleep(4, 3);
        return registry.find(code)
                .orElseThrow(() -> new NotFoundException("unknown code: " + code));
    }

    static void sleep(long base, long jitter) {
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(jitter + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
