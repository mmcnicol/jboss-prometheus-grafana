package io.github.jpg.servicea;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thin JAX-RS wrapper over {@link PatientDirectory}. The small simulated latency
 * stands in for a datasource call. Not unit-tested (glue): the logic lives in
 * {@link PatientDirectory}.
 */
@Path("patients")
@Produces(MediaType.APPLICATION_JSON)
public class PatientResource {

    private final PatientDirectory directory = new PatientDirectory();

    @GET
    public List<Map<String, Object>> list(@QueryParam("limit") int limit) {
        sleep(12, 8);
        return directory.list(limit);
    }

    @GET
    @Path("{ref}")
    public Map<String, Object> byRef(@PathParam("ref") String ref) {
        sleep(6, 5);
        return directory.find(ref)
                .orElseThrow(() -> new NotFoundException("no such patient: " + ref));
    }

    private static void sleep(long base, long jitter) {
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(jitter + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
