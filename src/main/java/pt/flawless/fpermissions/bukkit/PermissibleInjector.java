package pt.flawless.fpermissions.bukkit;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fpermissions.service.PermissionService;

/**
 * Troca, por reflexão, o campo {@code perm} do jogador por um {@link InjectedPermissible} e repõe o original.
 * Usar só a partir da thread principal.
 */
public class PermissibleInjector {

    private record Held(Object target, Field field, PermissibleBase original) {
    }

    private final PermissionService service;
    private final Map<UUID, Held> held = new ConcurrentHashMap<>();

    public PermissibleInjector(PermissionService service) {
        this.service = service;
    }

    public void inject(Player player) {
        inject(player, player.getUniqueId());
    }

    public void restore(Player player) {
        restore(player, player.getUniqueId());
    }

    /** Repõe o original de todos os jogadores injetados (desativação do plugin). */
    public void restoreAll() {
        for (UUID uuid : held.keySet()) {
            try {
                restore(uuid);
            } catch (RuntimeException e) { // um jogador com problema não impede os restantes
                try {
                    new FConsoleLogger("fPermissions").sendMessage("falha ao repor permissões de " + uuid + ": " + e, EMessageTypeEnum.ERROR);
                } catch (RuntimeException ignored) {
                    // sem servidor (testes)
                }
            }
        }
    }

    void inject(Object target, UUID uuid) {
        if (held.containsKey(uuid)) {
            return; // já injetado: manter o original verdadeiro
        }
        Field field = findField(target.getClass());
        try {
            PermissibleBase original = (PermissibleBase) field.get(target);
            field.set(target, new InjectedPermissible(original, uuid, service));
            held.put(uuid, new Held(target, field, original));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Não foi possível trocar o campo 'perm' de " + target.getClass().getName(), e);
        }
    }

    void restore(Object ignored, UUID uuid) {
        restore(uuid);
    }

    private void restore(UUID uuid) {
        Held h = held.remove(uuid);
        if (h == null) {
            return;
        }
        try {
            h.field().set(h.target(), h.original());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Não foi possível repor o campo 'perm' de " + h.target().getClass().getName(), e);
        }
    }

    private static Field findField(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("perm");
                if (PermissibleBase.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException e) {
                // continua na superclasse
            }
        }
        throw new IllegalStateException(
                "Campo 'perm' (PermissibleBase) não encontrado em " + type.getName() + " nem nas superclasses");
    }
}
