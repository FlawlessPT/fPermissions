package pt.flawless.fpermissions.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.permissions.ServerOperator;
import org.junit.jupiter.api.Test;
import pt.flawless.fpermissions.model.GroupDef;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.service.PermissionService;
import pt.flawless.fpermissions.storage.PermissionRepository;
import pt.flawless.fpermissions.storage.PlayerData;

class InjectedPermissibleTest {

    static class MemRepo extends PermissionRepository {
        final Set<String> perms;

        MemRepo(Set<String> perms) {
            super(null);
            this.perms = perms;
        }

        @Override
        public CompletableFuture<PlayerData> load(UUID uuid) {
            return CompletableFuture.completedFuture(new PlayerData(new HashSet<>(), new HashSet<>(perms)));
        }
    }

    private static InjectedPermissible make(boolean op, String... perms) throws Exception {
        Groups groups = Groups.parse("default", Map.of("default", new GroupDef(List.of(), List.of())));
        PermissionService service = new PermissionService(new MemRepo(Set.of(perms)), groups);
        UUID id = UUID.randomUUID();
        service.loadPlayer(id).join();
        ServerOperator opable = new ServerOperator() {
            public boolean isOp() {
                return op;
            }

            public void setOp(boolean value) {
            }
        };
        return new InjectedPermissible(opable, id, service);
    }

    @Test
    void grantedPermission() throws Exception {
        assertTrue(make(false, "fshop.admin").hasPermission("fshop.admin"));
    }

    @Test
    void missingPermission() throws Exception {
        assertFalse(make(false, "fshop.admin").hasPermission("outra"));
    }

    @Test
    void opIsIgnored() throws Exception {
        assertFalse(make(true).hasPermission("fshop.admin"));
    }

    @Test
    void pluginYmlDefaultIsIgnored() throws Exception {
        assertFalse(make(true).hasPermission(new Permission("x", PermissionDefault.TRUE) {
            @Override
            public void recalculatePermissibles() { // o construtor real exige Bukkit.getServer()
            }
        }));
    }

    @Test
    void isPermissionSetCoversGrantedAndDenied() throws Exception {
        InjectedPermissible p = make(false, "a.b", "-a.c");
        assertTrue(p.isPermissionSet("a.b"));
        assertTrue(p.isPermissionSet("a.c"));
        assertFalse(p.isPermissionSet("z"));
    }

    @Test
    void effectivePermissionsListLiteralGrants() throws Exception {
        Set<PermissionAttachmentInfo> infos = make(false, "a.b", "c.*", "-a.b").getEffectivePermissions();
        assertTrue(infos.isEmpty());
        InjectedPermissible p = make(false, "a.b", "c.*");
        assertEquals(1, p.getEffectivePermissions().size());
        assertEquals("a.b", p.getEffectivePermissions().iterator().next().getPermission());
    }
}
