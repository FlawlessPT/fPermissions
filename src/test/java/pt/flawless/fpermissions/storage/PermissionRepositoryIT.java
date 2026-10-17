package pt.flawless.fpermissions.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.flawless.fpermissions.TestDatabaseService;
import pt.flawless.fpermissions.TestDb;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRepositoryIT {

    private TestDatabaseService db;
    private PermissionRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        db = TestDb.service();
        TestDb.resetSchema(db);
        repo = new PermissionRepository(db);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(30, TimeUnit.SECONDS);
    }

    @Test
    void load_emptyForUnknownUuid() throws Exception {
        PlayerData data = await(repo.load(UUID.randomUUID()));
        assertTrue(data.groups().isEmpty());
        assertTrue(data.permissions().isEmpty());
    }

    @Test
    void group_addRemoveReturnWhetherRowChanged() throws Exception {
        UUID id = UUID.randomUUID();
        assertTrue(await(repo.addGroup(id, "vip")));
        assertFalse(await(repo.addGroup(id, "vip")));
        assertEquals(Set.of("vip"), await(repo.load(id)).groups());
        assertTrue(await(repo.removeGroup(id, "vip")));
        assertFalse(await(repo.removeGroup(id, "vip")));
        assertTrue(await(repo.load(id)).groups().isEmpty());
    }

    @Test
    void permission_addRemoveKeepsNegationVerbatim() throws Exception {
        UUID id = UUID.randomUUID();
        assertTrue(await(repo.addPermission(id, "-futilities.fly")));
        assertFalse(await(repo.addPermission(id, "-futilities.fly")));
        assertTrue(await(repo.addPermission(id, "futilities.heal")));
        assertEquals(Set.of("-futilities.fly", "futilities.heal"), await(repo.load(id)).permissions());
        assertTrue(await(repo.removePermission(id, "-futilities.fly")));
        assertFalse(await(repo.removePermission(id, "-futilities.fly")));
        assertEquals(Set.of("futilities.heal"), await(repo.load(id)).permissions());
    }

    @Test
    void dataOfOneUuidDoesNotAppearInAnother() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        await(repo.addGroup(a, "admin"));
        await(repo.addPermission(a, "x.y"));
        PlayerData other = await(repo.load(b));
        assertTrue(other.groups().isEmpty());
        assertTrue(other.permissions().isEmpty());
        assertTrue(await(repo.addGroup(b, "admin")));
    }
}
