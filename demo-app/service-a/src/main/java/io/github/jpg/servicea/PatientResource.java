package io.github.jpg.servicea;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in patient directory. Generic placeholder data — no real schema.
 * Small simulated latency stands in for a datasource call.
 */
@Path("patients")
@Produces(MediaType.APPLICATION_JSON)
public class PatientResource {

    private static final int TOTAL = 500;

    @GET
    public List<Map<String, Object>> list(@QueryParam("limit") int limit) {
        sleep(12, 8);
        int n = limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(patient(1000 + i));
        }
        return out;
    }

    @GET
    @Path("{ref}")
    public Map<String, Object> byRef(@PathParam("ref") String ref) {
        sleep(6, 5);
        int id;
        try {
            id = Integer.parseInt(ref.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            throw new NotFoundException("no such patient: " + ref);
        }
        if (id < 1000 || id >= 1000 + TOTAL) {
            throw new NotFoundException("no such patient: " + ref);
        }
        return patient(id);
    }

    private static Map<String, Object> patient(int id) {
        return Map.of(
                "reference", String.format("PT-%05d", id),
                "ward", "Ward " + (char) ('A' + (id % 6)),
                "active", id % 7 != 0);
    }

    private static void sleep(long base, long jitter) {
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(jitter + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
