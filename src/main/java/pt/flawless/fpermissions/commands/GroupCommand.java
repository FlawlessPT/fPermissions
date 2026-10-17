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
 * /grupo: add, remove, list, info, groups, check, reload, help. Só {@code /grupo} sem argumentos (e {@code list} sem
 * nome) é livre; o resto exige
 * {@code fpermissions.admin} (verificado aqui, o comando não tem {@code permission:} no plugin.yml).
 */
public class GroupCommand implements CommandExecutor {
    private static final String UNKNOWN = "§cSubcomando desconhecido.";
    private static final String UNKNOWN_ADMIN = UNKNOWN + " Usa /grupo help para ver os comandos disponíveis.";

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
            // sem argumentos mostra os grupos de quem escreve (a consola não tem grupos: remete para o help)
            if (args.length == 0) {
                if (sender instanceof Player) {
                    list(sender, new String[] {"list"});
                } else {
                    sender.sendMessage("§cUsa /grupo help para ver os comandos disponíveis.");
                }
                return true;
            }
            if (sub.equals("help")) {
                help(sender);
                return true;
            }
            // quem não é admin só conhece "list" sem nome; o resto responde como um subcomando inexistente
            if (!admin && !(sub.equals("list") && args.length == 1)) {
                sender.sendMessage(UNKNOWN);
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
                default -> sender.sendMessage(UNKNOWN_ADMIN);
            }
        } catch (RuntimeException e) {
            sender.sendMessage("§cNão foi possível concluir a operação.");
            FConsoleLogger.sendConsoleMessage("falha em /grupo: " + e, EMessageTypeEnum.ERROR);
        }
        return true;
    }

    /** /grupo help: só para admins (como o /money help). Pode ser a consola. */
    private void help(CommandSender sender) {
        if (!Replies.isAdmin(sender)) {
            sender.sendMessage("§cNão tens permissão.");
            return;
        }
        sender.sendMessage("§e/grupo §7- Mostra os teus grupos");
        sender.sendMessage("§e/grupo list [jogador] §7- Mostra os grupos de um jogador (sem nome, os teus)");
        sender.sendMessage("§e/grupo add <jogador> <grupo> §7- Põe um jogador num grupo");
        sender.sendMessage("§e/grupo remove <jogador> <grupo> §7- Tira um jogador de um grupo");
        sender.sendMessage("§e/grupo info <grupo> §7- Mostra a herança e as permissões de um grupo");
        sender.sendMessage("§e/grupo groups §7- Lista os grupos configurados");
        sender.sendMessage("§e/grupo check <jogador> <permissão> §7- Diz se um jogador tem uma permissão e de onde vem");
        sender.sendMessage("§e/grupo reload §7- Recarrega os grupos do config.yml");
        sender.sendMessage("§e/grupo help §7- Mostra esta lista");
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
                add ? service.addGroup(Replies.uuidOf(sender, name), group) : service.removeGroup(Replies.uuidOf(sender, name), group),
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
        Replies.reply(plugin, sender, "/grupo list", service.getGroups(Replies.uuidOf(sender, name)),
                (Set<String> groups) -> {
                    java.util.List<String> shown = new java.util.ArrayList<>();
                    shown.add(service.getDefaultGroup() + " (por omissão)");
                    shown.addAll(groups);
                    sender.sendMessage("§eGrupos de " + name + ": §7" + String.join(", ", shown));
                });
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
        Replies.reply(plugin, sender, "/grupo check", service.check(Replies.uuidOf(sender, name), perm), c -> {
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
