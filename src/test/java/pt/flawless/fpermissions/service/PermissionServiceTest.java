package pt.flawless.fpermissions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.flawless.fpermissions.model.GroupDef;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.storage.PermissionRepository;
import pt.flawless.fpermissions.storage.PlayerData;

class PermissionServiceTest {

    /** Repositório em memória; conta chamadas e pode falhar o load. */
    static class FakeRepo extends PermissionRepository {
        final Map<UUID, Set<String>> groups = new ConcurrentHashMap<>();
        final Map<UUID, Set<String>> perms = new ConcurrentHashMap<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean failLoad;
        volatile boolean manual;
        final List<CompletableFuture<PlayerData>> pending = new java.util.concurrent.CopyOnWriteArrayList<>();

        PlayerData snapshot(UUID uuid) {
            return new PlayerData(
                    new HashSet<>(groups.getOrDefault(uuid, Set.of())), new HashSet<>(perms.getOrDefault(uuid, Set.of())));
        }

        FakeRepo() {
            super(null);
        }

        @Override
        public CompletableFuture<PlayerData> load(UUID uuid) {
            calls.incrementAndGet();
            if (manual) {
                CompletableFuture<PlayerData> f = new CompletableFuture<>();
                pending.add(f);
                return f;
            }
            if (failLoad) {
                return CompletableFuture.failedFuture(new IllegalStateException("bd em baixo"));
            }
            return CompletableFuture.completedFuture(new PlayerData(
                    new HashSet<>(groups.getOrDefault(uuid, Set.of())), new HashSet<>(perms.getOrDefault(uuid, Set.of()))));
        }

        private CompletableFuture<Boolean> add(Map<UUID, Set<String>> m, UUID u, String v) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(m.computeIfAbsent(u, k -> ConcurrentHashMap.newKeySet()).add(v));
        }

        private CompletableFuture<Boolean> remove(Map<UUID, Set<String>> m, UUID u, String v) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(m.getOrDefault(u, Set.of()).remove(v));
        }

        @Override
        public CompletableFuture<Boolean> addGroup(UUID uuid, String group) {
            return add(groups, uuid, group);
        }

        @Override
        public CompletableFuture<Boolean> removeGroup(UUID uuid, String group) {
            return remove(groups, uuid, group);
        }

        @Override
        public CompletableFuture<Boolean> addPermission(UUID uuid, String permission) {
            return add(perms, uuid, permission);
        }

        @Override
        public CompletableFuture<Boolean> removePermission(UUID uuid, String permission) {
            return remove(perms, uuid, permission);
        }
    }

    private final UUID id = UUID.randomUUID();
    private FakeRepo repo;
    private PermissionService service;

    private static Groups groups(String adminPerm) throws Exception {
        Map<String, GroupDef> m = new LinkedHashMap<>();
        m.put("membro", new GroupDef(List.of(), List.of("fshop.use")));
        m.put("vip", new GroupDef(List.of("membro"), List.of("vip.kit")));
        m.put("admin", new GroupDef(List.of("vip"), List.of(adminPerm)));
        return Groups.parse("membro", m);
    }

    @BeforeEach
    void setUp() throws Exception {
        repo = new FakeRepo();
        service = new PermissionService(repo, groups("admin.*"));
    }

    private static Throwable cause(CompletableFuture<?> f) {
        CompletionException e = assertThrows(CompletionException.class, f::join);
        return e.getCause();
    }

    @Test
    void has_falseWhenNotLoaded() {
        assertFalse(service.has(id, "fshop.use"));
        assertFalse(service.has(null, "x"));
        assertFalse(service.has(id, null));
    }

    @Test
    void loadPlayer_thenHas() {
        repo.groups.put(id, Set.of("admin"));
        repo.perms.put(id, Set.of("extra.node"));
        service.loadPlayer(id).join();
        assertTrue(service.has(id, "fshop.use"));
        assertTrue(service.has(id, "vip.kit"));
        assertTrue(service.has(id, "admin.anything"));
        assertTrue(service.has(id, "extra.node"));
        assertFalse(service.has(id, "other"));
        assertTrue(service.isLoaded(id));
        assertEquals(Set.of(id), service.loadedPlayers());
    }

    @Test
    void negationFromPlayerBeatsGroupGrant() {
        repo.perms.put(id, Set.of("-fshop.use"));
        service.loadPlayer(id).join();
        assertFalse(service.has(id, "fshop.use"));
    }

    @Test
    void addGroup_unknownGroupFails() {
        var f = service.addGroup(id, "fantasma");
        assertTrue(cause(f) instanceof IllegalArgumentException);
        assertEquals(0, repo.calls.get());
    }

    @Test
    void addGroup_refreshesCacheOfOnlinePlayer() {
        service.loadPlayer(id).join();
        assertFalse(service.has(id, "vip.kit"));
        assertTrue(service.addGroup(id, " VIP ").join());
        assertTrue(service.has(id, "vip.kit"));
        assertEquals(Set.of("vip"), repo.groups.get(id));
    }

    @Test
    void mutationOfOfflinePlayerLeavesCacheEmpty() {
        service.addGroup(id, "vip").join();
        service.addPlayerPermission(id, "a.b").join();
        service.removeGroup(id, "vip").join();
        service.removePlayerPermission(id, "a.b").join();
        assertFalse(service.isLoaded(id));
        assertTrue(service.loadedPlayers().isEmpty());
    }

    @Test
    void addPlayerPermission_storesNormalized() {
        assertTrue(service.addPlayerPermission(id, " FShop.Admin ").join());
        assertEquals(Set.of("fshop.admin"), repo.perms.get(id));
        assertTrue(service.removePlayerPermission(id, "FSHOP.admin").join());
        assertFalse(service.removePlayerPermission(id, "fshop.admin").join());
    }

    @Test
    void addPlayerPermission_malformedFails() {
        assertTrue(cause(service.addPlayerPermission(id, "a..b")) instanceof IllegalArgumentException);
        assertTrue(cause(service.removePlayerPermission(id, "a..b")) instanceof IllegalArgumentException);
        assertTrue(cause(service.addPlayerPermission(id, null)) instanceof IllegalArgumentException);
        assertEquals(0, repo.calls.get());
    }

    @Test
    void nullAndBlankNamesFailWithIllegalArgument() {
        assertTrue(cause(service.addGroup(id, null)) instanceof IllegalArgumentException);
        assertTrue(cause(service.addGroup(id, "  ")) instanceof IllegalArgumentException);
        assertTrue(cause(service.removeGroup(id, null)) instanceof IllegalArgumentException);
        assertTrue(cause(service.removeGroup(id, " ")) instanceof IllegalArgumentException);
        assertTrue(cause(service.removeGroup(id, "g".repeat(65))) instanceof IllegalArgumentException);
        assertTrue(cause(service.addGroup(id, "g".repeat(65))) instanceof IllegalArgumentException);
        assertEquals(0, repo.calls.get());
    }

    @Test
    void removeGroup_unknownNameStillCallsRepository() {
        repo.groups.put(id, ConcurrentHashMap.newKeySet());
        repo.groups.get(id).add("fantasma");
        assertTrue(service.removeGroup(id, " Fantasma ").join());
        assertFalse(service.removeGroup(id, "fantasma").join());
    }

    @Test
    void setGroups_recomputesOnlinePlayers() throws Exception {
        repo.groups.put(id, Set.of("admin"));
        service.loadPlayer(id).join();
        assertTrue(service.has(id, "admin.x"));
        int before = repo.calls.get();
        service.setGroups(groups("outra.*"));
        assertFalse(service.has(id, "admin.x"));
        assertTrue(service.has(id, "outra.x"));
        assertEquals(before, repo.calls.get());
    }

    @Test
    void groupInDbMissingFromConfig_isIgnored() {
        repo.groups.put(id, Set.of("fantasma", "vip"));
        service.loadPlayer(id).join();
        assertTrue(service.has(id, "vip.kit"));
        assertTrue(service.check(id, "vip.kit").join().granted());
    }

    @Test
    void check_reportsSource() {
        repo.groups.put(id, Set.of("admin"));
        repo.perms.put(id, Set.of("solo.node"));
        assertEquals("grupo:admin", service.check(id, "admin.x").join().source());
        assertEquals("jogador", service.check(id, "solo.node").join().source());
        assertFalse(service.isLoaded(id));
    }

    @Test
    void loadPlayer_repositoryFailure_propagates() {
        repo.failLoad = true;
        assertTrue(cause(service.loadPlayer(id)) instanceof IllegalStateException);
        assertFalse(service.isLoaded(id));
    }

    @Test
    void unload_andEffective() {
        service.loadPlayer(id).join();
        assertTrue(service.effective(id).isPresent());
        service.unload(id);
        assertTrue(service.effective(id).isEmpty());
        assertFalse(service.isLoaded(id));
    }

    @Test
    void groupApi() {
        assertEquals(Set.of("membro", "vip", "admin"), service.getGroupNames());
        assertEquals("vip", service.getGroup("VIP").orElseThrow().name());
        assertTrue(service.getGroup(null).isEmpty());
        repo.groups.put(id, Set.of("vip"));
        repo.perms.put(id, Set.of("a.b"));
        assertEquals(Set.of("vip"), service.getGroups(id).join());
        assertEquals(Set.of("a.b"), service.getPlayerPermissions(id).join());
    }

    @Test
    void outOfOrderRefreshes_newestWins() {
        service.loadPlayer(id).join();
        repo.manual = true;
        var a = service.addGroup(id, "vip");
        var b = service.addGroup(id, "admin");
        repo.pending.get(1).complete(new PlayerData(Set.of("vip", "admin"), Set.of()));
        repo.pending.get(0).complete(new PlayerData(Set.of("vip"), Set.of()));
        assertTrue(a.join());
        assertTrue(b.join());
        assertTrue(service.has(id, "admin.x"));
    }

    @Test
    void unloadDuringLoad_staysUnloaded() {
        repo.manual = true;
        var f = service.loadPlayer(id);
        service.unload(id);
        repo.pending.get(0).complete(new PlayerData(Set.of("vip"), Set.of()));
        f.join();
        assertFalse(service.isLoaded(id));
    }

    @Test
    void unloadDuringRefresh_noEntry() {
        service.loadPlayer(id).join();
        repo.manual = true;
        var f = service.addPlayerPermission(id, "a.b");
        service.unload(id);
        repo.pending.get(0).complete(new PlayerData(Set.of(), Set.of("a.b")));
        assertTrue(f.join());
        assertFalse(service.isLoaded(id));
        assertFalse(service.has(id, "a.b"));
    }

    @Test
    void refreshFailure_evictsAndStillCompletes() {
        repo.groups.put(id, Set.of("vip"));
        service.loadPlayer(id).join();
        repo.manual = true;
        var f = service.addPlayerPermission(id, "a.b");
        repo.pending.get(0).completeExceptionally(new IllegalStateException("bd"));
        assertTrue(f.join());
        assertFalse(service.isLoaded(id));
        assertFalse(service.has(id, "vip.kit"));
    }

    @Test
    void setGroupsDuringLoad_finalEntryUsesNewGroups() throws Exception {
        repo.manual = true;
        var f = service.loadPlayer(id);
        service.setGroups(groups("outra.*"));
        repo.pending.get(0).complete(new PlayerData(Set.of("admin"), Set.of()));
        f.join();
        assertTrue(service.has(id, "outra.x"));
        assertFalse(service.has(id, "admin.x"));
    }

    @Test
    void check_nullPermission_isDenied() {
        var r = service.check(id, null).join();
        assertFalse(r.granted());
        assertEquals(null, r.source());
    }
}
