package pt.flawless.fpermissions.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.model.InvalidGroupsException;

class GroupsLoaderTest {

    private static MemoryConfiguration base() {
        MemoryConfiguration c = new MemoryConfiguration();
        c.set("default-group", "membro");
        c.createSection("groups.membro");
        return c;
    }

    @Test
    void configNormalCarrega() throws Exception {
        MemoryConfiguration c = base();
        c.set("groups.admin.inherits", List.of("membro"));
        c.set("groups.admin.permissions", List.of("*"));
        Groups g = GroupsLoader.load(c);
        assertEquals(Set.of("membro", "admin"), g.names());
        assertTrue(g.effective(List.of("admin"), List.of()).check("x.y").granted());
    }

    @Test
    void permissionsEscalarRecusado() {
        MemoryConfiguration c = base();
        c.set("groups.admin.permissions", "*");
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class, () -> GroupsLoader.load(c));
        assertTrue(e.getMessage().contains("admin") && e.getMessage().contains("permissions"));
    }

    @Test
    void inheritsEscalarRecusado() {
        MemoryConfiguration c = base();
        c.set("groups.vip.inherits", "membro");
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class, () -> GroupsLoader.load(c));
        assertTrue(e.getMessage().contains("vip") && e.getMessage().contains("inherits"));
    }

    @Test
    void ausentesValemVazio() throws Exception {
        Groups g = GroupsLoader.load(base());
        assertTrue(g.info("membro").orElseThrow().inherits().isEmpty());
        assertTrue(g.info("membro").orElseThrow().permissions().isEmpty());
    }

    @Test
    void corpoNuloValeGrupoVazio() throws Exception {
        MemoryConfiguration c = new MemoryConfiguration();
        c.set("default-group", "membro");
        c.set("groups.membro", null);
        // null remove a chave no MemoryConfiguration: grupo some, logo o default falta
        assertThrows(InvalidGroupsException.class, () -> GroupsLoader.load(c));
    }

    @Test
    void semSecaoGroupsFalhaPorDefault() {
        MemoryConfiguration c = new MemoryConfiguration();
        c.set("default-group", "membro");
        InvalidGroupsException e = assertThrows(InvalidGroupsException.class, () -> GroupsLoader.load(c));
        assertTrue(e.getMessage().contains("membro"));
    }
}
