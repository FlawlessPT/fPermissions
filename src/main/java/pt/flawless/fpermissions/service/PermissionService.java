package pt.flawless.fpermissions.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final Logger LOG = Logger.getLogger("fPermissions");

    private record Entry(PlayerData data, PermissionSet set) {
    }

    private final PermissionRepository repository;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private final Map<UUID, Long> tickets = new ConcurrentHashMap<>();
    private volatile Groups groups;
    private volatile java.util.function.Consumer<UUID> onRefreshed = u -> { };

    public PermissionService(PermissionRepository repository, Groups groups) {
        this.repository = repository;
        this.groups = groups;
    }

    /** Chamado (em qualquer thread) quando as permissões de um jogador em cache mudam. */
    public void setOnRefreshed(java.util.function.Consumer<UUID> onRefreshed) {
        this.onRefreshed = onRefreshed;
    }

    /** Troca os grupos (reload da config) e recalcula, sem BD, o conjunto de cada jogador na cache. */
    public void setGroups(Groups groups) {
        this.groups = groups;
        cache.replaceAll((uuid, e) -> new Entry(e.data(), compute(groups, e.data())));
        cache.keySet().forEach(onRefreshed);
    }

    private static PermissionSet compute(Groups groups, PlayerData data) {
        return groups.effective(data.groups(), data.permissions());
    }

    /** Carrega o jogador da BD para a cache; o futuro falha com a exceção do repositório. */
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        long ticket = seq.incrementAndGet();
        tickets.put(uuid, ticket);
        return read(uuid, ticket).whenComplete((v, ex) -> {
            if (ex != null) {
                tickets.remove(uuid, ticket);
            }
        });
    }

    /** Lê da BD e guarda só se o bilhete ainda for o último do jogador (o mais recente ganha; unload invalida). */
    private CompletableFuture<Void> read(UUID uuid, long ticket) {
        return repository.load(uuid).thenAccept(data -> {
            Groups used = groups;
            Set<String> unknown = used.unknown(data.groups());
            if (!unknown.isEmpty()) {
                LOG.warning("Grupos desconhecidos (não estão na config) ignorados para " + uuid + ": " + unknown);
            }
            tickets.computeIfPresent(uuid, (k, current) -> {
                if (current == ticket) {
                    cache.put(uuid, new Entry(data, compute(used, data)));
                }
                return current;
            });
            if (used != groups) { // setGroups correu entretanto
                cache.computeIfPresent(uuid, (k, e) -> new Entry(e.data(), compute(groups, e.data())));
            }
        });
    }

    public void unload(UUID uuid) {
        tickets.remove(uuid);
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

    public String getDefaultGroup() {
        return groups.defaultGroup();
    }

    @Override
    public Set<String> getGroupNames() {
        return groups.names();
    }

    @Override
    public Optional<GroupInfo> getGroup(String name) {
        return groups.info(name);
    }
    /**
     * Depois de uma alteração, recarrega o jogador só se já estiver na cache. Se o recarregamento falhar, a entrada é
     * retirada (falha fechada) e o futuro completa na mesma, porque a alteração na BD foi feita.
     */
    private CompletableFuture<Boolean> refreshed(UUID uuid, CompletableFuture<Boolean> change) {
        return change.thenCompose(changed -> {
            long ticket = seq.incrementAndGet();
            // computeIfPresent: nunca cria bilhete (nem entrada) para um jogador que já saiu
            if (tickets.computeIfPresent(uuid, (k, v) -> ticket) == null) {
                return CompletableFuture.completedFuture(changed);
            }
            return read(uuid, ticket).handle((v, ex) -> {
                if (ex != null) {
                    // so evicta se o bilhete ainda for o deste refresh; um mais novo nao e afetado
                    tickets.computeIfPresent(uuid, (k, cur) -> {
                        if (cur != ticket) {
                            return cur;
                        }
                        cache.remove(uuid);
                        return null;
                    });
                    LOG.log(Level.WARNING, "Falha a recarregar as permissões de " + uuid + "; entrada retirada", ex);
                } else {
                    onRefreshed.accept(uuid);
                }
                return changed;
            });
        });
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
