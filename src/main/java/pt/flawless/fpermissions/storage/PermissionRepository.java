package pt.flawless.fpermissions.storage;

import pt.flawless.fapi.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Acesso aos grupos e permissões de cada jogador, nas tabelas {@code fpermissions_player_groups} e
 * {@code fpermissions_player_permissions}.
 *
 * <p>Todos os métodos são assíncronos: correm no pool do {@link DatabaseService} e o futuro falha com a
 * {@link SQLException} original. Nunca se deve esperar ({@code join()}/{@code get()}) por outro futuro de
 * base de dados dentro de uma tarefa do pool. Os valores chegam já normalizados; o repositório não os altera.
 *
 * <p>A classe não é {@code final} e o construtor não valida o argumento, para os testes poderem usar duplos.
 */
public class PermissionRepository {

    private static final String SELECT_GROUPS = "SELECT group_name FROM fpermissions_player_groups WHERE uuid = ?";
    private static final String SELECT_PERMISSIONS =
            "SELECT permission FROM fpermissions_player_permissions WHERE uuid = ?";
    private static final String ADD_GROUP =
            "INSERT IGNORE INTO fpermissions_player_groups (uuid, group_name) VALUES (?, ?)";
    private static final String REMOVE_GROUP =
            "DELETE FROM fpermissions_player_groups WHERE uuid = ? AND group_name = ?";
    private static final String ADD_PERMISSION =
            "INSERT IGNORE INTO fpermissions_player_permissions (uuid, permission) VALUES (?, ?)";
    private static final String REMOVE_PERMISSION =
            "DELETE FROM fpermissions_player_permissions WHERE uuid = ? AND permission = ?";

    private final DatabaseService database;

    public PermissionRepository(DatabaseService database) {
        this.database = database;
    }

    /** Carrega grupos e permissões do jogador numa só ligação; conjuntos vazios se não houver nada. */
    public CompletableFuture<PlayerData> load(UUID uuid) {
        return database.supplyAsync(connection -> new PlayerData(
                selectColumn(connection, SELECT_GROUPS, uuid), selectColumn(connection, SELECT_PERMISSIONS, uuid)));
    }

    /** @return {@code true} se o jogador passou a ter o grupo, {@code false} se já o tinha */
    public CompletableFuture<Boolean> addGroup(UUID uuid, String group) {
        return change(ADD_GROUP, uuid, group);
    }

    /** @return {@code true} se o grupo foi removido, {@code false} se o jogador não o tinha */
    public CompletableFuture<Boolean> removeGroup(UUID uuid, String group) {
        return change(REMOVE_GROUP, uuid, group);
    }

    /** @return {@code true} se a permissão (ou negação) foi acrescentada, {@code false} se já existia */
    public CompletableFuture<Boolean> addPermission(UUID uuid, String permission) {
        return change(ADD_PERMISSION, uuid, permission);
    }

    /** @return {@code true} se a permissão (ou negação) foi removida, {@code false} se não existia */
    public CompletableFuture<Boolean> removePermission(UUID uuid, String permission) {
        return change(REMOVE_PERMISSION, uuid, permission);
    }

    private CompletableFuture<Boolean> change(String sql, UUID uuid, String value) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, value);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private static Set<String> selectColumn(Connection connection, String sql, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> values = new HashSet<>();
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
                return values;
            }
        }
    }
}
