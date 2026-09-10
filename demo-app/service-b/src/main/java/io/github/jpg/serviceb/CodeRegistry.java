package io.github.jpg.serviceb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Plain reference data — no JAX-RS, so it unit-tests without a runtime. */
public class CodeRegistry {

    private static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        CODES.put("DC01", "Routine discharge");
        CODES.put("DC02", "Discharge to community care");
        CODES.put("DC03", "Self-discharge");
        CODES.put("DC04", "Transfer to another provider");
        CODES.put("DC05", "Deceased");
    }

    public Map<String, String> all() {
        return new LinkedHashMap<>(CODES);
    }

    public Optional<Map<String, Object>> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String key = code.toUpperCase();
        String description = CODES.get(key);
        if (description == null) {
            return Optional.empty();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", key);
        out.put("description", description);
        return Optional.of(out);
    }

    public boolean isValid(String code) {
        return code != null && CODES.containsKey(code.toUpperCase());
    }
}
