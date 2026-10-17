package pt.flawless.fpermissions.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PermissionNodeTest {

    @Test
    void normalizaParaMinusculasSemEspacos() {
        assertEquals("futilities.fly", PermissionNode.normalize(" FUtilities.Fly "));
    }

    @Test
    void aceitaNosValidos() {
        for (String ok : new String[] {"*", "-*", "a.*", "-a.b", "barberians.staff"}) {
            assertEquals(ok, PermissionNode.normalize(ok));
        }
    }

    @Test
    void recusaNosInvalidos() {
        for (String bad : new String[] {"", "-", ".", "a..b", ".*", "a b", "--a", null}) {
            assertThrows(IllegalArgumentException.class, () -> PermissionNode.normalize(bad), String.valueOf(bad));
        }
    }

    @Test
    void cobre() {
        assertTrue(PermissionNode.covers("futilities.*", "futilities.fly"));
        assertFalse(PermissionNode.covers("futilities.*", "futilities"));
        assertTrue(PermissionNode.covers("*", "x"));
        assertTrue(PermissionNode.covers("a.b", "a.b"));
        assertFalse(PermissionNode.covers("a.b", "a.c"));
    }
}
