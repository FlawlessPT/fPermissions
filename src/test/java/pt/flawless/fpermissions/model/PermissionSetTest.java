package pt.flawless.fpermissions.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pt.flawless.fapi.permissions.PermissionCheck;

class PermissionSetTest {

    private static PermissionSet set(Map<String, List<String>> m) {
        return PermissionSet.of(m);
    }

    @Test
    void igualdadeExata() {
        PermissionCheck c = set(Map.of("grupo:vip", List.of("a.b"))).check("a.b");
        assertEquals(true, c.granted());
        assertEquals("grupo:vip", c.source());
    }

    @Test
    void curingaDePrefixoEEstrela() {
        assertEquals(true, set(Map.of("g", List.of("a.*"))).check("a.b.c").granted());
        assertEquals(false, set(Map.of("g", List.of("a.*"))).check("a").granted());
        assertEquals(true, set(Map.of("g", List.of("*"))).check("x.y").granted());
    }

    @Test
    void negacaoIndividualGanhaAoAdmin() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("grupo:admin", List.of("*"));
        m.put("jogador", List.of("-futilities.fly"));
        PermissionSet s = set(m);
        PermissionCheck fly = s.check("futilities.fly");
        assertEquals(false, fly.granted());
        assertEquals("jogador", fly.source());
        PermissionCheck shop = s.check("fshop.admin");
        assertEquals(true, shop.granted());
        assertEquals("grupo:admin", shop.source());
    }

    @Test
    void negacaoComCuringaGanhaConcessaoExata() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("grupo:vip", List.of("fshop.admin"));
        m.put("jogador", List.of("-fshop.*"));
        PermissionCheck c = set(m).check("fshop.admin");
        assertEquals(false, c.granted());
        assertEquals("jogador", c.source());
    }

    @Test
    void consultaNormalizada() {
        assertEquals(true, set(Map.of("g", List.of("fshop.admin"))).check("FSHOP.Admin").granted());
    }

    @Test
    void consultaLenientNaoLanca() {
        PermissionSet s = set(Map.of("g", List.of("a.b")));
        for (String q : new String[] {null, "a b", "foo:bar"}) {
            PermissionCheck c = s.check(q);
            assertEquals(false, c.granted(), String.valueOf(q));
            assertNull(c.source(), String.valueOf(q));
        }
        assertEquals(true, set(Map.of("g", List.of("*"))).check("foo:bar").granted());
        PermissionSet shop = set(Map.of("g", List.of("fshop.admin")));
        assertEquals(shop.check("fshop.admin"), shop.check(" FSHOP.Admin "));
        assertEquals(true, shop.check(" FSHOP.Admin ").granted());
    }

    @Test
    void nadaCobre() {
        PermissionCheck c = set(Map.of("g", List.of("a.b"))).check("x");
        assertEquals(false, c.granted());
        assertNull(c.source());
    }

    @Test
    void literalGrantsExcluiCuringasENegados() {
        PermissionSet s = set(Map.of("g", List.of("a.b", "c.*", "*", "d", "-d")));
        assertEquals(Set.of("a.b"), s.literalGrants());
    }
}
