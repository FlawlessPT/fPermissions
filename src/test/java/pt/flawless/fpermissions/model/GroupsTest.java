package pt.flawless.fpermissions.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroupsTest {

    private static GroupDef def(List<String> inherits, String... perms) {
        return new GroupDef(inherits, List.of(perms));
    }

    private static Map<String, GroupDef> map(Object... kv) {
        Map<String, GroupDef> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (GroupDef) kv[i + 1]);
        }
        return m;
    }

    private static Groups basic() throws InvalidGroupsException {
        return Groups.parse("membro", map(
                "membro", def(List.of(), "fshop.use"),
                "vip", def(List.of("membro")),
                "admin", def(List.of("vip"), "*")));
    }

    @Test
    void recusaDefaultInexistente() {
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("x", map("membro", def(List.of()))));
        assertTrue(e.getMessage().contains("x"));
    }

    @Test
    void recusaHerancaInexistente() {
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("a", map("a", def(List.of("fantasma")))));
        assertTrue(e.getMessage().contains("fantasma"));
    }

    @Test
    void recusaCiclo() {
        assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("a", map("a", def(List.of("b")), "b", def(List.of("a")))));
    }

    @Test
    void recusaAutoHeranca() {
        assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("a", map("a", def(List.of("a")))));
    }

    @Test
    void recusaNoInvalido() {
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("a", map("a", def(List.of(), "a..b"))));
        assertTrue(e.getMessage().contains("a..b"));
    }

    @Test
    void nomesSemMaiusculas() throws Exception {
        Groups g = Groups.parse("Membro", map("MEMBRO", def(List.of()), "Admin", def(List.of("membro"))));
        assertTrue(g.exists("ADMIN"));
        assertTrue(g.exists("admin"));
        assertEquals(Set.of("membro", "admin"), g.names());
        assertEquals("membro", g.defaultGroup());
    }

    @Test
    void expandJuntaDefaultEHerdados() throws Exception {
        assertEquals(Set.of("membro", "vip"), basic().expand(List.of("vip")));
    }

    @Test
    void diamanteContaUmaVez() throws Exception {
        Groups g = Groups.parse("a", map(
                "a", def(List.of()),
                "b", def(List.of("a")),
                "c", def(List.of("a")),
                "d", def(List.of("b", "c"))));
        List<String> out = List.copyOf(g.expand(List.of("d")));
        assertEquals(4, out.size());
        assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(out));
    }

    @Test
    void desconhecidosIgnoradosMasReportados() throws Exception {
        Groups g = basic();
        assertEquals(Set.of("membro"), g.expand(List.of("inexistente")));
        assertEquals(Set.of("inexistente"), g.unknown(List.of("Inexistente", "vip")));
    }

    @Test
    void effectiveAdminNegaEConcede() throws Exception {
        PermissionSet s = basic().effective(List.of("admin"), List.of("-futilities.fly"));
        assertFalse(s.check("futilities.fly").granted());
        assertTrue(s.check("fshop.admin").granted());
    }

    @Test
    void effectiveVazioSoTemDefault() throws Exception {
        PermissionSet s = basic().effective(List.of(), List.of());
        assertTrue(s.check("fshop.use").granted());
        assertEquals("grupo:membro", s.check("fshop.use").source());
        assertFalse(s.check("fshop.admin").granted());
    }

    @Test
    void recusaNomesDuplicadosAposNormalizar() {
        assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("membro", map("MEMBRO", def(List.of()), "membro", def(List.of()))));
    }

    @Test
    void namesImutavel() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> basic().names().add("x"));
    }

    @Test
    void infoDevolveGrupo() throws Exception {
        var info = basic().info("VIP").orElseThrow();
        assertEquals("vip", info.name());
        assertEquals(List.of("membro"), info.inherits());
        assertTrue(basic().info("x").isEmpty());
    }

    @Test
    void nomeDeGrupoComMaisDe64CaracteresRecusado() {
        String longo = "g".repeat(65);
        var ex = assertThrows(InvalidGroupsException.class,
                () -> Groups.parse("membro", map("membro", def(List.of()), longo, def(List.of()))));
        assertTrue(ex.getMessage().contains(longo));
        assertDoesNotThrow(() -> Groups.parse("membro", map("membro", def(List.of()), "g".repeat(64), def(List.of()))));
    }

    @Test
    void groupNameWithSpaceIsRejected() {
        Map<String, GroupDef> m = new LinkedHashMap<>();
        m.put("Super Admin", new GroupDef(List.of(), List.of()));
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class, () -> Groups.parse("super admin", m));
        assertTrue(e.getMessage().contains("super admin"));
    }
}
