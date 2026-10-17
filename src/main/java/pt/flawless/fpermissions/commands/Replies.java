package pt.flawless.fpermissions.commands;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;

/** Utilitários partilhados pelos comandos: permissão de admin, resolução de nomes e resposta na thread principal. */
final class Replies {
    static final String ADMIN = "fpermissions.admin";

    private Replies() {}

    /** Quem não é jogador (consola) conta como admin. */
    static boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(ADMIN);
    }

    /** Servidor offline-mode: o UUID deriva do nome, serve também para nomes nunca vistos. */
    static UUID uuidOf(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    /** Espera o futuro e responde na thread principal; erros de validação mostram a mensagem, o resto vai ao log. */
    static <T> void reply(JavaPlugin plugin, CommandSender sender, String what, CompletableFuture<T> future,
                          Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> {
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause() : error;
                    if (cause == null) {
                        if (isReachable(sender)) {
                            onSuccess.accept(value);
                        }
                    } else if (cause instanceof IllegalArgumentException) {
                        send(sender, "§c" + cause.getMessage());
                    } else {
                        FConsoleLogger.sendConsoleMessage("falha em " + what + ": " + cause, EMessageTypeEnum.ERROR);
                        send(sender, "§cNão foi possível concluir a operação.");
                    }
                });
            } catch (RuntimeException e) { // por exemplo o plugin foi desativado entretanto
                FConsoleLogger.sendConsoleMessage("não foi possível responder a " + what + ": " + e.getMessage(),
                        EMessageTypeEnum.ERROR);
            }
        });
    }

    private static boolean isReachable(CommandSender sender) {
        return !(sender instanceof Player) || ((Player) sender).isOnline();
    }

    private static void send(CommandSender sender, String message) {
        if (isReachable(sender)) {
            sender.sendMessage(message);
        }
    }
}
