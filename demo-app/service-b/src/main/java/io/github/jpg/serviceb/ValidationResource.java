package io.github.jpg.serviceb;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Thin JAX-RS wrapper over {@link DischargeValidator}. Returns 200 for a valid
 * payload, 400 (with the problems) otherwise — so a load test exercises both
 * paths. Not unit-tested (glue).
 */
@Path("validate")
public class ValidationResource {

    private final DischargeValidator validator = new DischargeValidator(new CodeRegistry());

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validate(Map<String, Object> payload) {
        CodeResource.sleep(6, 5);
        List<String> problems = validator.validate(payload);
        if (problems.isEmpty()) {
            return Response.ok(Map.of("valid", true)).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("valid", false, "problems", problems))
                .build();
    }
}
