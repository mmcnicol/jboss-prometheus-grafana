package io.github.jpg.serviceb;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Stand-in reference-data lookup for discharge codes. Generic placeholder data. */
@Path("codes")
@Produces(MediaType.APPLICATION_JSON)
public class CodeResource {

    private static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        CODES.put("DC01", "Routine discharge");
        CODES.put("DC02", "Discharge to community care");
        CODES.put("DC03", "Self-discharge");
        CODES.put("DC04", "Transfer to another provider");
        CODES.put("DC05", "Deceased");
    }

    @GET
    public Map<String, String> all() {
        sleep(5, 4);
        return CODES;
    }

    @GET
    @Path("{code}")
    public Map<String, Object> one(@PathParam("code") String code) {
        sleep(4, 3);
        String description = CODES.get(code.toUpperCase());
        if (description == null) {
            throw new NotFoundException("unknown code: " + code);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code.toUpperCase());
        out.put("description", description);
        return out;
    }

    static void sleep(long base, long jitter) {
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(jitter + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isValidCode(String code) {
        return code != null && CODES.containsKey(code.toUpperCase());
    }
}
