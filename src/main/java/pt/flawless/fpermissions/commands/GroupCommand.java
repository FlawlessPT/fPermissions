package pt.flawless.fpermissions.commands;

import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.fpermissions.service.PermissionService;

/**
 * /grupo: add, remove, list, info, groups, check, reload. Só {@code list} sem nome é livre; o resto exige
 * {@code fpermissions.admin} (verificado aqui, o comando não tem {@code permission:} no plugin.yml).
 */
public class GroupCommand implements CommandExecutor {
    private static final String USAGE_ADMIN = "§cUso: /grupo add|remove <jogador> <grupo> | list [jogador] | info <grupo>"
            + " | groups | check <jogador> <permissão> | reload";
    private static final String USAGE_PLAYER = "§cUso: /grupo list";

    private final JavaPlugin plugin;
    private final PermissionService service;
    private final Supplier<String> reloader;

    public GroupCommand(JavaPlugin plugin, PermissionService service, Supplier<String> reloader) {
        this.plugin = plugin;
        this.service = service;
        this.reloader = reloader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = Replies.isAdmin(sender);
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        try {
            // quem não é admin só conhece "list" sem nome; o resto responde como um subcomando inexistente
            if (!admin && !(sub.equals("list") && args.length == 1)) {
                sender.sendMessage(USAGE_PLAYER);
                return true;
            }
            switch (sub) {
                case "add", "remove" -> addRemove(sender, sub, args);
                case "list" -> list(sender, args);
                case "info" -> info(sender, args);
                case "groups" -> {
                    if (args.length != 1) {
                        sender.sendMessage("§cUso: /grupo groups");
                        return true;
                    }
                    Set<String> names = service.getGroupNames();
                    sender.sendMessage("§eGrupos: §7" + (names.isEmpty() ? "nenhum" : String.join(", ", names)));
                }
                case "check" -> check(sender, args);
                case "reload" -> {
                    if (args.length != 1) {
                        sender.sendMessage("§cUso: /grupo reload");
                        return true;
                    }
                    String error = reloader.get();
                    sender.sendMessage(error == null ? "§aGrupos recarregados." : "§cNão foi possível recarregar: " + error);
                }
                default -> sender.sendMessage(USAGE_ADMIN);
            }
        } catch (RuntimeException e) {
            sender.sendMessage("§cNão foi possível concluir a operação.");
            FConsoleLogger.sendConsoleMessage("falha em /grupo: " + e, EMessageTypeEnum.ERROR);
        }
        return true;
    }

    private void addRemove(CommandSender sender, String sub, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUso: /grupo " + sub + " <jogador> <grupo>");
            return;
        }
        boolean add = sub.equals("add");
        String name = args[1];
        String group = args[2];
        Replies.reply(plugin, sender, "/grupo " + sub,
                add ? service.addGroup(Replies.uuidOf(name), group) : service.removeGroup(Replies.uuidOf(name), group),
                changed -> sender.sendMessage(changed
                        ? "§a" + name + (add ? " foi posto no grupo " : " foi tirado do grupo ") + "§7" + group + "§a."
                        : "§e" + name + (add ? " já estava no grupo " : " não estava no grupo ") + group + "."));
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage("§cUso: /grupo list [jogador]");
            return;
        }
        if (args.length == 1 && !(sender instanceof Player)) {
            sender.sendMessage("§cUso: /grupo list <jogador>");
            return;
        }
        String name = args.length == 2 ? args[1] : sender.getName();
        Replies.reply(plugin, sender, "/grupo list", service.getGroups(Replies.uuidOf(name)),
                (Set<String> groups) -> sender.sendMessage(groups.isEmpty()
                        ? "§e" + name + " não tem grupos."
                        : "§eGrupos de " + name + ": §7" + String.join(", ", groups)));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§cUso: /grupo info <grupo>");
            return;
        }
        service.getGroup(args[1]).ifPresentOrElse(g -> {
            sender.sendMessage("§eGrupo §7" + g.name());
            sender.sendMessage("§eHerda: §7" + (g.inherits().isEmpty() ? "nenhum" : String.join(", ", g.inherits())));
            sender.sendMessage("§ePermissões: §7" + (g.permissions().isEmpty() ? "nenhuma" : String.join(", ", g.permissions())));
        }, () -> sender.sendMessage("§cO grupo " + args[1] + " não existe."));
    }

    private void check(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUso: /grupo check <jogador> <permissão>");
            return;
        }
        String name = args[1];
        String perm = args[2];
        Replies.reply(plugin, sender, "/grupo check", service.check(Replies.uuidOf(name), perm), c -> {
            if (c.source() == null) {
                sender.sendMessage("§e" + name + " §cnão tem §ea permissão §7" + perm + "§e (origem: §7nenhuma entrada§e).");
            } else if (c.granted()) {
                sender.sendMessage("§e" + name + " §atem §ea permissão §7" + perm + "§e (origem: §7" + c.source() + "§e).");
            } else {
                sender.sendMessage("§e" + name + " §cnão tem §ea permissão §7" + perm + "§e: está §cnegada §e(origem: §7"
                        + c.source() + "§e).");
            }
        });
    }
}
