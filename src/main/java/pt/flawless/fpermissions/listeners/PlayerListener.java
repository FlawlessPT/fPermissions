package pt.flawless.fpermissions.listeners;

import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fpermissions.bukkit.PermissibleInjector;
import pt.flawless.fpermissions.service.PermissionService;

/** Ciclo de vida das permissões de um jogador: carregar no pré-login, injetar ao entrar, limpar ao sair. */
public class PlayerListener implements Listener {

    public static final String LOAD_FAILED = "Não foi possível carregar as tuas permissões. Tenta mais tarde.";

    private final PermissionService service;
    private final PermissibleInjector injector;
    private final Consumer<UUID> loader;
    private final Consumer<Player> kicker;
    private final FConsoleLogger logger = new FConsoleLogger("fPermissions");

    /** {@code loader} carrega o jogador de forma assíncrona e injeta-o; {@code kicker} expulsa-o (já adiado para o tick seguinte). */
    public PlayerListener(PermissionService service, PermissibleInjector injector, Consumer<UUID> loader,
                          Consumer<Player> kicker) {
        this.service = service;
        this.injector = injector;
        this.loader = loader;
        this.kicker = kicker;
    }

    /** Corre numa thread assíncrona, por isso pode bloquear à espera da BD. Falha = fail-closed. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return; // outro plugin já recusou: não carregar
        }
        try {
            service.loadPlayer(event.getUniqueId()).join();
        } catch (Throwable t) {
            error("falha ao carregar permissões de " + event.getName() + ": " + t);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, LOAD_FAILED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            onLoginRejected(event.getPlayer().getUniqueId());
        }
    }

    /** Login recusado por outro motivo depois do carregamento: não deixar a cache pendurada. */
    void onLoginRejected(UUID uuid) {
        service.unload(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            injector.inject(player);
        } catch (RuntimeException e) {
            error("falha ao injetar permissões de " + player.getName() + ": " + e);
            kicker.accept(player);
            return;
        }
        ensureLoaded(player.getUniqueId());
    }

    /** Numa reconexão o quit da sessão antiga limpa a cache carregada no pré-login da nova: recarregar. */
    void ensureLoaded(UUID uuid) {
        if (!service.isLoaded(uuid)) {
            loader.accept(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        injector.restore(player);
        service.unload(player.getUniqueId());
    }

    private void error(String message) {
        try {
            logger.sendMessage(message, EMessageTypeEnum.ERROR);
        } catch (RuntimeException ignored) {
            // sem servidor (testes): o log nunca pode quebrar o evento
        }
    }
}
