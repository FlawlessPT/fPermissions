package pt.flawless.fpermissions.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import pt.flawless.fapi.permissions.GroupInfo;
import pt.flawless.fapi.permissions.PermissionCheck;
import pt.flawless.fapi.permissions.PermissionsApi;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.model.PermissionNode;
import pt.flawless.fpermissions.model.PermissionSet;
import pt.flawless.fpermissions.storage.PermissionRepository;
import pt.flawless.fpermissions.storage.PlayerData;

/** Implementa o {@link PermissionsApi} com cache dos jogadores online (dados da BD e conjunto já calculado). */
public class PermissionService implements PermissionsApi {

    private static final int MAX_GROUP_LENGTH = 64;

    private record Entry(PlayerData data, PermissionSet set) {
    }

    private final PermissionRepository repository;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private volatile Groups groups;

    public PermissionService(PermissionRepository repository, Groups groups) {
        this.repository = repository;
        this.groups = groups;
    }

    /** Troca os grupos (reload da config) e recalcula, sem BD, o conjunto de cada jogador na cache. */
    public void setGroups(Groups groups) {
        this.groups = groups;
        cache.replaceAll((uuid, e) -> new Entry(e.data(), compute(groups, e.data())));
    }

    private static PermissionSet compute(Groups groups, PlayerData data) {
        return groups.effective(data.groups(), data.permissions());
    }

    /** Carrega o jogador da BD para a cache; o futuro falha com a exceção do repositório. */
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return repository.load(uuid).thenAccept(data -> cache.put(uuid, new Entry(data, compute(groups, data))));
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public Set<UUID> loadedPlayers() {
        return Set.copyOf(cache.keySet());
    }

    public Optional<PermissionSet> effective(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid)).map(Entry::set);
    }

    @Override
    public boolean has(UUID uuid, String permission) {
        if (uuid == null || permission == null) {
            return false;
        }
        Entry e = cache.get(uuid);
        return e != null && e.set().check(permission).granted();
    }

    @Override
    public CompletableFuture<PermissionCheck> check(UUID uuid, String permission) {
        return guard(() -> {
            requireUuid(uuid);
            return repository.load(uuid).thenApply(data -> compute(groups, data).check(permission));
        });
    }

    @Override
    public CompletableFuture<Set<String>> getGroups(UUID uuid) {
        return guard(() -> {
            requireUuid(uuid);
            return repository.load(uuid).thenApply(PlayerData::groups);
        });
    }

    @Override
    public CompletableFuture<Boolean> addGroup(UUID uuid, String group) {
        return guard(() -> {
            requireUuid(uuid);
            String name = groupName(group);
            if (!groups.exists(name)) {
                throw new IllegalArgumentException("grupo inexistente: " + name);
            }
            return refreshed(uuid, repository.addGroup(uuid, name));
        });
    }

    @Override
    public CompletableFuture<Boolean> removeGroup(UUID uuid, String group) {
        return guard(() -> {
            requireUuid(uuid);
            return refreshed(uuid, repository.removeGroup(uuid, groupName(group)));
        });
    }

    @Override
    public CompletableFuture<Set<String>> getPlayerPermissions(UUID uuid) {
        return guard(() -> {
            requireUuid(uuid);
            return repository.load(uuid).thenApply(PlayerData::permissions);
        });
    }

    @Override
    public CompletableFuture<Boolean> addPlayerPermission(UUID uuid, String permission) {
        return guard(() -> {
            requireUuid(uuid);
            return refreshed(uuid, repository.addPermission(uuid, PermissionNode.normalize(permission)));
        });
    }

    @Override
    public CompletableFuture<Boolean> removePlayerPermission(UUID uuid, String permission) {
        return guard(() -> {
            requireUuid(uuid);
            return refreshed(uuid, repository.removePermission(uuid, PermissionNode.normalize(permission)));
        });
    }

    @Override
    public Set<String> getGroupNames() {
        return groups.names();
    }

    @Override
    public Optional<GroupInfo> getGroup(String name) {
        return groups.info(name);
    }

    /** Depois de uma alteração, recarrega o jogador só se já estiver na cache. */
    private CompletableFuture<Boolean> refreshed(UUID uuid, CompletableFuture<Boolean> change) {
        return change.thenCompose(changed -> cache.containsKey(uuid)
                ? loadPlayer(uuid).thenApply(v -> changed)
                : CompletableFuture.completedFuture(changed));
    }

    private static void requireUuid(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid nulo");
        }
    }

    private static String groupName(String group) {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("nome de grupo vazio");
        }
        String name = group.trim().toLowerCase(Locale.ROOT);
        if (name.length() > MAX_GROUP_LENGTH) {
            throw new IllegalArgumentException("nome de grupo com mais de " + MAX_GROUP_LENGTH + " caracteres");
        }
        return name;
    }

    /** Converte exceções síncronas de validação em futuros falhados. */
    private static <T> CompletableFuture<T> guard(Supplier<CompletableFuture<T>> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
