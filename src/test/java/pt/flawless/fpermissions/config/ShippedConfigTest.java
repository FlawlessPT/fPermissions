package pt.flawless.fpermissions.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import pt.flawless.fpermissions.model.Groups;

class ShippedConfigTest {

    @Test
    void defaultConfigParsesAndMembersKeepBasicCommands() throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        Groups groups = GroupsLoader.load(yaml);
        var member = groups.effective(java.util.Set.of(), java.util.Set.of());
        for (String p : new String[] {"minecraft.command.tell", "minecraft.command.me", "bukkit.command.help",
                "bukkit.broadcast.user"}) {
            assertTrue(member.check(p).granted(), p);
        }
    }
}
