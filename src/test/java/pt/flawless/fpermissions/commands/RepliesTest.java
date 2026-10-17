package pt.flawless.fpermissions.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RepliesTest {

    @Test
    void onlinePlayerWinsCaseInsensitive() {
        assertEquals("JoaoSousa", Replies.resolveName("joaosousa", List.of("JoaoSousa"), List.of("joaosousa")));
    }

    @Test
    void knownOfflineNameFixesCase() {
        assertEquals("JoaoSousa", Replies.resolveName("JOAOSOUSA", List.of(), List.of("Outro", "JoaoSousa")));
    }

    @Test
    void unknownKeepsTypedName() {
        assertEquals("Ninguem", Replies.resolveName("Ninguem", List.of("A"), List.of("B")));
    }
}
