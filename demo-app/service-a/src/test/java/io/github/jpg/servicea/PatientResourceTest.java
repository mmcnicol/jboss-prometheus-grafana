package io.github.jpg.servicea;

import org.junit.jupiter.api.Test;

import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientResourceTest {

    private final PatientResource resource = new PatientResource();

    @Test
    void listRespectsLimit() {
        List<Map<String, Object>> out = resource.list(5);
        assertEquals(5, out.size());
        assertTrue(out.get(0).get("reference").toString().startsWith("PT-"));
    }

    @Test
    void listDefaultsAndCaps() {
        assertEquals(20, resource.list(0).size());
        assertEquals(100, resource.list(9999).size());
    }

    @Test
    void byRefReturnsKnownPatient() {
        assertEquals("PT-01000", resource.byRef("PT-01000").get("reference"));
    }

    @Test
    void byRefUnknownIs404() {
        assertThrows(NotFoundException.class, () -> resource.byRef("PT-99999"));
        assertThrows(NotFoundException.class, () -> resource.byRef("nonsense"));
    }
}
