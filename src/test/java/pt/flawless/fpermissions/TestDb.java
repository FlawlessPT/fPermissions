package pt.flawless.fpermissions;

import org.junit.jupiter.api.Assumptions;
import pt.flawless.fapi.database.DatabaseService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/** Ajudante dos testes de integracao: so corre contra a base literalmente chamada flawless_test. */
public final class TestDb {

    private static final String[] VARS = {"FLAWLESS_DB_HOST", "FLAWLESS_DB_PORT", "FLAWLESS_DB_NAME",
            "FLAWLESS_DB_USER", "FLAWLESS_DB_PASSWORD"};

    private TestDb() {
    }

    /** Ignora o teste se faltarem variaveis ou se a base nao for flawless_test (os testes fazem DROP). */
    public static TestDatabaseService service() {
        for (String v : VARS) {
            String value = System.getenv(v);
            Assumptions.assumeTrue(value != null && !value.isEmpty(), v + " nao definida");
        }
        Assumptions.assumeTrue("flawless_test".equals(System.getenv("FLAWLESS_DB_NAME")),
                "FLAWLESS_DB_NAME nao e flawless_test");
        return new TestDatabaseService(System.getenv("FLAWLESS_DB_HOST"), System.getenv("FLAWLESS_DB_PORT"),
                System.getenv("FLAWLESS_DB_NAME"), System.getenv("FLAWLESS_DB_USER"),
                System.getenv("FLAWLESS_DB_PASSWORD"));
    }

    /** Apaga so as duas tabelas fpermissions_* (nunca schema_version) e recria-as, uma instrucao do V001 de cada vez. */
    public static void resetSchema(DatabaseService database) throws Exception {
        // cada instrucao termina com ';' no fim da linha (regra do migrate)
        String[] statements = readMigration().split(";[ \\t]*\\r?\\n");
        database.runAsync(connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute("DROP TABLE IF EXISTS fpermissions_player_groups");
                st.execute("DROP TABLE IF EXISTS fpermissions_player_permissions");
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (trimmed.endsWith(";")) {
                        trimmed = trimmed.substring(0, trimmed.length() - 1);
                    }
                    if (!trimmed.isEmpty()) {
                        st.execute(trimmed);
                    }
                }
            }
        }).get(30, TimeUnit.SECONDS);
    }

    private static String readMigration() {
        try (InputStream in = TestDb.class.getResourceAsStream("/db/fpermissions/V001__create_permissions.sql")) {
            if (in == null) {
                throw new IllegalStateException("V001 nao encontrado no classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
