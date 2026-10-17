package pt.flawless.fpermissions.bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;
import pt.flawless.fpermissions.service.PermissionService;

/**
 * Permissível que decide só pelo {@link PermissionService}: OP e {@code default:} do plugin.yml não concedem nada.
 * Limitações: permissões de outros plugins via {@code addAttachment} são ignoradas pelo {@code hasPermission},
 * e {@code isOp()} continua a refletir o estado real de OP.
 */
public class InjectedPermissible extends PermissibleBase {

    private final UUID uuid;
    private final PermissionService service;

    public InjectedPermissible(ServerOperator opable, UUID uuid, PermissionService service) {
        super(opable);
        this.uuid = uuid;
        this.service = service;
    }

    @Override
    public boolean hasPermission(String name) {
        return service.has(uuid, name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return perm != null && service.has(uuid, perm.getName());
    }

    /** Verdadeiro se alguma entrada cobre a permissão, concedida ou negada. */
    @Override
    public boolean isPermissionSet(String name) {
        return name != null && service.effective(uuid).map(s -> s.check(name).source() != null).orElse(false);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return perm != null && isPermissionSet(perm.getName());
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        Set<PermissionAttachmentInfo> out = new HashSet<>();
        service.effective(uuid).ifPresent(s -> {
            for (String node : s.literalGrants()) {
                out.add(new PermissionAttachmentInfo(this, node, null, true));
            }
        });
        return out;
    }

    /** Nada a recalcular; o super-construtor chama isto antes de os campos existirem. */
    @Override
    public void recalculatePermissions() {
    }
}
