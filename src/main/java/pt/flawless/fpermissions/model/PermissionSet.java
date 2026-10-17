package pt.flawless.fpermissions.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import pt.flawless.fapi.permissions.PermissionCheck;

/** Conjunto imutável de nós (já normalizados) por origem; a negação ganha sempre à concessão. */
public final class PermissionSet {

    private final Map<String, List<String>> nodesBySource;

    private PermissionSet(Map<String, List<String>> nodesBySource) {
        this.nodesBySource = nodesBySource;
    }

    /** As chaves são as origens ({@code "grupo:vip"}, {@code "jogador"}); a ordem do mapa é a ordem de reporte. */
    public static PermissionSet of(Map<String, ? extends Collection<String>> nodesBySource) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        nodesBySource.forEach((source, nodes) -> copy.put(source, List.copyOf(nodes)));
        return new PermissionSet(copy);
    }

    /** Consulta lenient (trim + minúsculas, sem validação, nunca lança; nulo dá negado); a negação ganha. */
    public PermissionCheck check(String permission) {
        if (permission == null) {
            return new PermissionCheck(false, null);
        }
        String query = permission.trim().toLowerCase(Locale.ROOT);
        String granter = null;
        for (Map.Entry<String, List<String>> e : nodesBySource.entrySet()) {
            for (String node : e.getValue()) {
                if (node.startsWith("-")) {
                    if (PermissionNode.covers(node.substring(1), query)) {
                        return new PermissionCheck(false, e.getKey());
                    }
                } else if (granter == null && PermissionNode.covers(node, query)) {
                    granter = e.getKey();
                }
            }
        }
        return granter != null ? new PermissionCheck(true, granter) : new PermissionCheck(false, null);
    }

    /** Nós positivos sem curinga que não estão negados. */
    public Set<String> literalGrants() {
        Set<String> out = new LinkedHashSet<>();
        for (List<String> nodes : nodesBySource.values()) {
            for (String node : nodes) {
                if (!node.startsWith("-") && !node.endsWith("*") && check(node).granted()) {
                    out.add(node);
                }
            }
        }
        return out;
    }
}
