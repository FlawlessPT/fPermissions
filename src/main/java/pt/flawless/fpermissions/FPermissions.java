package pt.flawless.fpermissions;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.database.DatabaseService;
import pt.flawless.fapi.database.Databases;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fapi.permissions.PermissionsApi;
import pt.flawless.fpermissions.bukkit.PermissibleInjector;
import pt.flawless.fpermissions.commands.GroupCommand;
import pt.flawless.fpermissions.commands.PermCommand;
import pt.flawless.fpermissions.config.GroupsLoader;
import pt.flawless.fpermissions.listeners.PlayerListener;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.model.InvalidGroupsException;
import pt.flawless.fpermissions.service.PermissionService;
import pt.flawless.fpermissions.storage.PermissionRepository;

public class FPermissions extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());
    private boolean enabledOk = false;
    private PermissionService service;
    private PermissibleInjector injector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Groups groups;
        try {
            groups = GroupsLoader.load(getConfig());
        } catch (InvalidGroupsException e) {
            consoleLogger.sendMessage("config.yml inválida: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            DatabaseService database = Databases.get();
            database.migrate(this, List.of("db/fpermissions/V001__create_permissions.sql"));
            service = new PermissionService(new PermissionRepository(database), groups);
        } catch (SQLException | RuntimeException e) {
            // inclui DatabaseUnavailableException (RuntimeException)
            consoleLogger.sendMessage("não foi possível iniciar as permissões: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        injector = new PermissibleInjector(service);
        getServer().getServicesManager().register(PermissionsApi.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new PlayerListener(service, injector, this::loadOnline,
                p -> Bukkit.getScheduler().runTask(this, () -> p.kickPlayer(PlayerListener.LOAD_FAILED))), this);
        getCommand("grupo").setExecutor(new GroupCommand(this, service, this::reloadGroups));
        getCommand("perm").setExecutor(new PermCommand(this, service));

        for (Player online : Bukkit.getOnlinePlayers()) { // /reload com jogadores online
            loadOnline(online.getUniqueId());
        }

        enabledOk = true;
        consoleLogger.sendEnablePluginMessage();
    }

    private void loadOnline(UUID uuid) {
        service.loadPlayer(uuid).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) { // saiu entretanto
                service.unload(uuid);
            } else if (ex != null) {
                consoleLogger.sendMessage("falha ao carregar permissões de " + player.getName() + ": " + ex, EMessageTypeEnum.ERROR);
                service.unload(uuid);
                player.kickPlayer(PlayerListener.LOAD_FAILED);
            } else {
                try {
                    injector.inject(player);
                } catch (RuntimeException e) {
                    consoleLogger.sendMessage("falha ao injetar permissões de " + player.getName() + ": " + e, EMessageTypeEnum.ERROR);
                    player.kickPlayer(PlayerListener.LOAD_FAILED);
                }
            }
        }));
    }

    /** Relê o config.yml. Devolve {@code null} em sucesso, ou a mensagem de erro (grupos anteriores mantidos). */
    public String reloadGroups() {
        if (service == null) {
            return "O plugin não está ativo.";
        }
        try {
            reloadConfig();
            service.setGroups(GroupsLoader.load(getConfig()));
            return null;
        } catch (InvalidGroupsException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return "Não foi possível ler o config.yml.";
        }
    }

    @Override
    public void onDisable() {
        if (enabledOk) {
            injector.restoreAll();
            for (UUID uuid : service.loadedPlayers()) {
                service.unload(uuid);
            }
            consoleLogger.sendDisablePluginMessage();
        }
    }
}
