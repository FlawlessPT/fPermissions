package pt.flawless.fpermissions.listeners;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Desativa o OP: bloqueia {@code /op} e {@code /deop} (jogadores e consola) e retira o OP a quem o tenha. */
public class OpListener implements Listener {

    private static final String MESSAGE = "§cO OP está desativado. Usa o sistema de permissões (/grupo e /perm).";
    private static final Set<String> BLOCKED = Set.of("op", "deop");

    /** Retira o OP a todos (ops.json incluído); chamar no arranque. */
    public static void deopAll() {
        for (OfflinePlayer op : Bukkit.getOperators()) {
            op.setOp(false);
        }
    }

    private static boolean isOpCommand(String line) {
        String root = line.startsWith("/") ? line.substring(1) : line;
        int space = root.indexOf(' ');
        if (space >= 0) {
            root = root.substring(0, space);
        }
        root = root.toLowerCase(Locale.ROOT);
        return BLOCKED.contains(root.startsWith("minecraft:") ? root.substring("minecraft:".length()) : root);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isOpCommand(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MESSAGE);
        }
    }

    /** Cobre a consola e o RCON (o RemoteServerCommandEvent estende este evento). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (isOpCommand(event.getCommand())) {
            event.setCancelled(true);
            CommandSender sender = event.getSender();
            sender.sendMessage(MESSAGE);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().isOp()) {
            event.getPlayer().setOp(false);
        }
    }
}
