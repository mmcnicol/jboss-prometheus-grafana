package io.github.jpg.servicea;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plain domain logic for the patient directory — no JAX-RS, so it unit-tests
 * without a JAX-RS runtime. {@link PatientResource} is a thin wrapper.
 */
public class PatientDirectory {

    static final int FIRST_ID = 1000;
    static final int TOTAL = 500;

    public List<Map<String, Object>> list(int limit) {
        int n = limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(patient(FIRST_ID + i));
        }
        return out;
    }

    public Optional<Map<String, Object>> find(String ref) {
        int id;
        try {
            id = Integer.parseInt(ref == null ? "" : ref.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (id < FIRST_ID || id >= FIRST_ID + TOTAL) {
            return Optional.empty();
        }
        return Optional.of(patient(id));
    }

    private static Map<String, Object> patient(int id) {
        return Map.of(
                "reference", String.format("PT-%05d", id),
                "ward", "Ward " + (char) ('A' + (id % 6)),
                "active", id % 7 != 0);
    }
}
