package io.github.jpg.serviceb;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceBTest {

    private final CodeRegistry codes = new CodeRegistry();
    private final DischargeValidator validator = new DischargeValidator(codes);

    @Test
    void listsAllCodes() {
        assertTrue(codes.all().containsKey("DC01"));
        assertEquals(5, codes.all().size());
    }

    @Test
    void lookupIsCaseInsensitiveAndEmptyForUnknown() {
        assertEquals("DC02", codes.find("dc02").orElseThrow().get("code"));
        assertFalse(codes.find("ZZ99").isPresent());
        assertFalse(codes.find(null).isPresent());
    }

    @Test
    void validPayloadHasNoProblems() {
        assertTrue(validator.validate(Map.of(
                "patientReference", "PT-01000", "dischargeCode", "DC01")).isEmpty());
    }

    @Test
    void invalidPayloadListsProblems() {
        assertEquals(2, validator.validate(Map.of()).size());
        assertEquals(1, validator.validate(Map.of("patientReference", "PT-1", "dischargeCode", "NOPE")).size());
    }
}
