package pt.flawless.fpermissions.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.flawless.fpermissions.bukkit.PermissibleInjector;
import pt.flawless.fpermissions.model.GroupDef;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.service.PermissionService;
import pt.flawless.fpermissions.storage.PermissionRepository;
import pt.flawless.fpermissions.storage.PlayerData;

class PlayerListenerTest {

    static class Repo extends PermissionRepository {
        volatile boolean fail;

        Repo() {
            super(null);
        }

        @Override
        public CompletableFuture<PlayerData> load(UUID uuid) {
            return fail
                    ? CompletableFuture.failedFuture(new IllegalStateException("bd em baixo"))
                    : CompletableFuture.completedFuture(new PlayerData(new HashSet<>(), new HashSet<>()));
        }
    }

    private final UUID id = UUID.randomUUID();
    private Repo repo;
    private PermissionService service;
    private PlayerListener listener;
    private List<UUID> loads;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, GroupDef> m = new LinkedHashMap<>();
        m.put("membro", new GroupDef(List.of(), List.of("fshop.use")));
        repo = new Repo();
        service = new PermissionService(repo, Groups.parse("membro", m));
        loads = new java.util.ArrayList<>();
        listener = new PlayerListener(service, new PermissibleInjector(service), u -> {
            loads.add(u);
            service.loadPlayer(u);
        }, p -> { });
    }

    private AsyncPlayerPreLoginEvent preLogin() {
        return new AsyncPlayerPreLoginEvent("Joao", InetAddress.getLoopbackAddress(), id);
    }

    @Test
    void preLoginFailureKicksAndCachesNothing() {
        repo.fail = true;
        AsyncPlayerPreLoginEvent e = preLogin();
        listener.onPreLogin(e);
        assertEquals(Result.KICK_OTHER, e.getLoginResult());
        assertFalse(service.isLoaded(id));
    }

    @Test
    void preLoginSuccessLoads() {
        AsyncPlayerPreLoginEvent e = preLogin();
        listener.onPreLogin(e);
        assertEquals(Result.ALLOWED, e.getLoginResult());
        assertTrue(service.isLoaded(id));
    }

    @Test
    void rejectedLoginUnloads() {
        listener.onPreLogin(preLogin());
        listener.onLoginRejected(id);
        assertFalse(service.isLoaded(id));
    }

    @Test
    void reconnectStaleQuitThenJoinReloads() {
        listener.onPreLogin(preLogin()); // nova ligação carrega
        service.unload(id); // quit síncrono da sessão antiga
        assertFalse(service.isLoaded(id));
        listener.ensureLoaded(id); // join da nova sessão
        assertEquals(List.of(id), loads);
        assertTrue(service.isLoaded(id));
    }

    @Test
    void joinWhenLoadedDoesNotReload() {
        listener.onPreLogin(preLogin());
        listener.ensureLoaded(id);
        assertTrue(loads.isEmpty());
    }

    @Test
    void preLoginAlreadyDeniedDoesNotLoad() {
        AsyncPlayerPreLoginEvent e = preLogin();
        e.disallow(Result.KICK_BANNED, "banido");
        listener.onPreLogin(e);
        assertFalse(service.isLoaded(id));
        assertEquals(Result.KICK_BANNED, e.getLoginResult());
    }
}
