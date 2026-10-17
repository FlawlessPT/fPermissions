package pt.flawless.fpermissions;

import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.logs.FConsoleLogger;

public class FPermissions extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());

    @Override
    public void onEnable() {
        consoleLogger.sendEnablePluginMessage();
    }

    @Override
    public void onDisable() {
        consoleLogger.sendDisablePluginMessage();
    }
}
