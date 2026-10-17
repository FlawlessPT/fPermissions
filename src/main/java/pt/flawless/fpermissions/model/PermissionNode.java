package pt.flawless.fpermissions.model;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PermissionNode {

    private static final Pattern VALID =
            Pattern.compile("^-?(\\*|[a-z0-9_][a-z0-9_-]*(\\.[a-z0-9_][a-z0-9_-]*)*(\\.\\*)?)$");

    private PermissionNode() {
    }

    /**
     * Põe o nó em minúsculas, sem espaços, e valida-o ({@code -} inicial para negações, {@code *} e {@code a.b.*}
     * como curingas).
     *
     * @throws IllegalArgumentException se o nó for nulo ou inválido
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("permissão inválida: null");
        }
        String node = raw.trim().toLowerCase(Locale.ROOT);
        // 128: tamanho da coluna; o INSERT IGNORE truncaria em silencio
        if (node.length() > 128 || !VALID.matcher(node).matches()) {
            throw new IllegalArgumentException("permissão inválida: " + raw);
        }
        return node;
    }

    /** Diz se {@code pattern} (sem o {@code -} inicial) cobre {@code permission}; {@code a.b.*} não cobre {@code a.b}. */
    public static boolean covers(String pattern, String permission) {
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            return permission.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(permission);
    }
}
