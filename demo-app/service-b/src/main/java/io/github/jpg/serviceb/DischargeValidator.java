package io.github.jpg.serviceb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain validation logic. Returns the list of problems (empty = valid). */
public class DischargeValidator {

    private final CodeRegistry codes;

    public DischargeValidator(CodeRegistry codes) {
        this.codes = codes;
    }

    public List<String> validate(Map<String, Object> payload) {
        List<String> problems = new ArrayList<>();

        Object ref = payload == null ? null : payload.get("patientReference");
        if (ref == null || ref.toString().isBlank()) {
            problems.add("patientReference is required");
        }

        Object code = payload == null ? null : payload.get("dischargeCode");
        if (!codes.isValid(code == null ? null : code.toString())) {
            problems.add("dischargeCode is missing or unknown");
        }

        return problems;
    }
}
