package pt.flawless.fpermissions.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pt.flawless.fapi.permissions.GroupInfo;

/** Grupos validados (nomes e nós normalizados, sem ciclos) e o cálculo das permissões efetivas. */
public final class Groups {

    private final String defaultGroup;
    private final Map<String, GroupDef> defs;

    private Groups(String defaultGroup, Map<String, GroupDef> defs) {
        this.defaultGroup = defaultGroup;
        this.defs = defs;
    }

    /** @throws InvalidGroupsException se o grupo por omissão ou uma herança não existir, houver ciclo ou um nó for inválido */
    public static Groups parse(String defaultGroup, Map<String, GroupDef> raw) throws InvalidGroupsException {
        Map<String, GroupDef> defs = new LinkedHashMap<>();
        for (Map.Entry<String, GroupDef> e : raw.entrySet()) {
            String name = norm(e.getKey());
            if (name.length() > 64) {
                throw new InvalidGroupsException("O nome do grupo '" + name + "' tem mais de 64 caracteres.");
            }
            List<String> inherits = new ArrayList<>();
            for (String parent : e.getValue().inherits()) {
                inherits.add(norm(parent));
            }
            List<String> perms = new ArrayList<>();
            for (String node : e.getValue().permissions()) {
                try {
                    perms.add(PermissionNode.normalize(node));
                } catch (IllegalArgumentException ex) {
                    throw new InvalidGroupsException("Grupo '" + name + "': permissão inválida '" + node + "'.");
                }
            }
            if (defs.put(name, new GroupDef(List.copyOf(inherits), List.copyOf(perms))) != null) {
                throw new InvalidGroupsException("O grupo '" + name + "' está definido mais de uma vez.");
            }
        }
        String def = norm(defaultGroup);
        if (!defs.containsKey(def)) {
            throw new InvalidGroupsException("O default-group '" + def + "' não existe em groups.");
        }
        for (Map.Entry<String, GroupDef> e : defs.entrySet()) {
            for (String parent : e.getValue().inherits()) {
                if (!defs.containsKey(parent)) {
                    throw new InvalidGroupsException(
                            "O grupo '" + e.getKey() + "' herda de '" + parent + "', que não existe.");
                }
            }
        }
        Set<String> done = new LinkedHashSet<>();
        for (String name : defs.keySet()) {
            checkCycle(name, defs, new ArrayList<>(), done);
        }
        return new Groups(def, defs);
    }

    /** Pesquisa em profundidade; {@code path} é o caminho atual, {@code done} os grupos já verificados. */
    private static void checkCycle(String name, Map<String, GroupDef> defs, List<String> path, Set<String> done)
            throws InvalidGroupsException {
        if (path.contains(name)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(name), path.size()));
            cycle.add(name);
            throw new InvalidGroupsException("Ciclo de herança nos grupos: " + String.join(" -> ", cycle) + ".");
        }
        if (!done.add(name)) {
            return;
        }
        path.add(name);
        for (String parent : defs.get(name).inherits()) {
            checkCycle(parent, defs, path, done);
        }
        path.remove(path.size() - 1);
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    public String defaultGroup() {
        return defaultGroup;
    }

    /** Nomes por ordem de declaração. */
    public Set<String> names() {
        return Collections.unmodifiableSet(defs.keySet());
    }

    public boolean exists(String name) {
        return name != null && defs.containsKey(norm(name));
    }

    public Optional<GroupInfo> info(String name) {
        if (!exists(name)) {
            return Optional.empty();
        }
        String n = norm(name);
        GroupDef d = defs.get(n);
        return Optional.of(new GroupInfo(n, d.inherits(), d.permissions()));
    }

    /** Grupo por omissão + explícitos existentes + herdados transitivamente, cada um uma vez; desconhecidos ignorados. */
    public Set<String> expand(Collection<String> explicit) {
        Set<String> out = new LinkedHashSet<>();
        add(defaultGroup, out);
        for (String g : explicit) {
            if (exists(g)) {
                add(norm(g), out);
            }
        }
        return out;
    }

    private void add(String name, Set<String> out) {
        if (out.add(name)) {
            defs.get(name).inherits().forEach(p -> add(p, out));
        }
    }

    /** Os nomes (normalizados) que não existem na config. */
    public Set<String> unknown(Collection<String> names) {
        Set<String> out = new LinkedHashSet<>();
        for (String g : names) {
            if (!exists(g)) {
                out.add(norm(g));
            }
        }
        return out;
    }

    /** Origens {@code "grupo:<nome>"} por cada grupo expandido, depois {@code "jogador"}. */
    public PermissionSet effective(Collection<String> explicitGroups, Collection<String> playerNodes) {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        for (String g : expand(explicitGroups)) {
            sources.put("grupo:" + g, defs.get(g).permissions());
        }
        sources.put("jogador", List.copyOf(playerNodes));
        return PermissionSet.of(sources);
    }
}
