package pt.flawless.fpermissions.commands;

import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fpermissions.service.PermissionService;

/** /perm add|remove &lt;jogador&gt; &lt;permissão&gt; e /perm list &lt;jogador&gt;: permissões individuais. */
public class PermCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final PermissionService service;

    public PermCommand(JavaPlugin plugin, PermissionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!Replies.isAdmin(sender)) {
            sender.sendMessage("§cNão tens permissão.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        try {
            switch (sub) {
                case "add", "remove" -> {
                    if (args.length != 3) {
                        sender.sendMessage("§cUso: /perm " + sub + " <jogador> <permissão>");
                        return true;
                    }
                    boolean add = sub.equals("add");
                    String name = args[1];
                    String perm = args[2];
                    Replies.reply(plugin, sender, "/perm " + sub,
                            add ? service.addPlayerPermission(Replies.uuidOf(name), perm)
                                : service.removePlayerPermission(Replies.uuidOf(name), perm),
                            changed -> sender.sendMessage(changed
                                    ? "§aPermissão §7" + perm + " §a" + (add ? "adicionada a " : "removida de ") + name + "."
                                    : "§e" + name + (add ? " já tinha a permissão " : " não tinha a permissão ") + perm + "."));
                }
                case "list" -> {
                    if (args.length != 2) {
                        sender.sendMessage("§cUso: /perm list <jogador>");
                        return true;
                    }
                    String name = args[1];
                    Replies.reply(plugin, sender, "/perm list", service.getPlayerPermissions(Replies.uuidOf(name)),
                            (Set<String> perms) -> sender.sendMessage(perms.isEmpty()
                                    ? "§e" + name + " não tem permissões individuais."
                                    : "§ePermissões de " + name + ": §7" + String.join(", ", perms)));
                }
                default -> sender.sendMessage("§cUso: /perm add|remove <jogador> <permissão> | /perm list <jogador>");
            }
        } catch (RuntimeException e) {
            sender.sendMessage("§cNão foi possível concluir a operação.");
            FConsoleLogger.sendConsoleMessage("falha em /perm: " + e, EMessageTypeEnum.ERROR);
        }
        return true;
    }
}
