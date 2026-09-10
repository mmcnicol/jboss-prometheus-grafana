package io.github.jpg.servicea;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientDirectoryTest {

    private final PatientDirectory directory = new PatientDirectory();

    @Test
    void listRespectsLimitAndBounds() {
        assertEquals(5, directory.list(5).size());
        assertEquals(20, directory.list(0).size());
        assertEquals(100, directory.list(9999).size());
        assertTrue(directory.list(1).get(0).get("reference").toString().startsWith("PT-"));
    }

    @Test
    void findReturnsKnownPatient() {
        assertEquals("PT-01000", directory.find("PT-01000").orElseThrow().get("reference"));
        assertEquals("PT-01499", directory.find("1499").orElseThrow().get("reference"));
    }

    @Test
    void findIsEmptyForUnknownOrGarbage() {
        assertFalse(directory.find("PT-99999").isPresent());
        assertFalse(directory.find("PT-00999").isPresent());
        assertFalse(directory.find("nonsense").isPresent());
        assertFalse(directory.find(null).isPresent());
    }
}
