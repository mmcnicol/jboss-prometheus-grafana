package io.github.jpg.serviceb;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates a discharge payload against the reference data. Returns 200 with
 * {@code {valid:true}} or 400 with the list of problems — so a load test
 * exercises both the 2xx and 4xx paths.
 */
@Path("validate")
public class ValidationResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validate(Map<String, Object> payload) {
        CodeResource.sleep(6, 5);

        List<String> problems = new ArrayList<>();
        Object ref = payload == null ? null : payload.get("patientReference");
        if (ref == null || ref.toString().isBlank()) {
            problems.add("patientReference is required");
        }
        Object code = payload == null ? null : payload.get("dischargeCode");
        if (!CodeResource.isValidCode(code == null ? null : code.toString())) {
            problems.add("dischargeCode is missing or unknown");
        }

        if (problems.isEmpty()) {
            return Response.ok(Map.of("valid", true)).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("valid", false, "problems", problems))
                .build();
    }
}
