package io.github.jpg.serviceb;

import org.junit.jupiter.api.Test;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceBTest {

    private final CodeResource codes = new CodeResource();
    private final ValidationResource validation = new ValidationResource();

    @Test
    void listsAllCodes() {
        assertTrue(codes.all().containsKey("DC01"));
    }

    @Test
    void lookupIsCaseInsensitiveAnd404sUnknown() {
        assertEquals("DC02", codes.one("dc02").get("code"));
        assertThrows(NotFoundException.class, () -> codes.one("ZZ99"));
    }

    @Test
    void validPayloadIs200() {
        Response r = validation.validate(Map.of("patientReference", "PT-01000", "dischargeCode", "DC01"));
        assertEquals(200, r.getStatus());
    }

    @Test
    void invalidPayloadIs400WithProblems() {
        Response r = validation.validate(Map.of("dischargeCode", "NOPE"));
        assertEquals(400, r.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getEntity();
        assertEquals(false, body.get("valid"));
    }
}
