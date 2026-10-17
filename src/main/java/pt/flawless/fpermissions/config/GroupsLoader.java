package pt.flawless.fpermissions.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import pt.flawless.fpermissions.model.GroupDef;
import pt.flawless.fpermissions.model.Groups;
import pt.flawless.fpermissions.model.InvalidGroupsException;

/** Lê {@code default-group} e {@code groups.<nome>.{inherits,permissions}} da config. */
public final class GroupsLoader {

    private GroupsLoader() {
    }

    public static Groups load(ConfigurationSection root) throws InvalidGroupsException {
        Map<String, GroupDef> defs = new LinkedHashMap<>();
        ConfigurationSection groups = root.getConfigurationSection("groups");
        if (groups != null) {
            for (String name : groups.getKeys(false)) {
                ConfigurationSection g = groups.getConfigurationSection(name);
                List<String> inherits = g == null ? List.of() : g.getStringList("inherits");
                List<String> perms = g == null ? List.of() : g.getStringList("permissions");
                defs.put(name, new GroupDef(inherits, perms));
            }
        }
        return Groups.parse(root.getString("default-group", ""), defs);
    }
}
