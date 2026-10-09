package com.example.winecellar.infrastructure;

import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WINE-59: migreringen för e-postverifiering, mot gamla tillstånd i en egen scratch-databas. En
 * färsk, tom testdatabas bevisar ingenting här (UPDATE ... WHERE IS NULL är då ett no-op, och
 * Hibernate har inte hunnit skapa några gamla varianter av user_tokens - se CLAUDE.md om
 * migreringsordning). Testet bygger därför:
 * - en users-tabell i det GAMLA formatet (utan email_verified) med riktiga rader, och
 * - user_tokens i två övergivna lägen från tidigare versioner av grenen: (A) som Hibernate skapade
 *   den med ett UK_-namngivet unikt constraint över (user_id, purpose) och en kolumn
 *   pending_password_hash, och (B) med partiellt unikt index, pending-kolumn och dubbletter av
 *   verifieringstokens utan något bredare constraint,
 * och kör både migreringsfilen och motsvarande del av schema.sql TVÅ gånger mot dem.
 */
class EmailVerificationMigrationIT extends SharedPostgres {

    private static final Path MIGRATION = Path.of("db/migrations/2026-10-09-add-email-verification.sql");
    private static final Path SCHEMA = Path.of("src/main/resources/schema.sql");

    @Test
    void skaMarkeraBefintligaKontonSomVerifieradeUtanAttRöraEttNyttOverifieratKonto() throws Exception {
        try (Connection connection = scratch("migration_wine59_users"); Statement statement = connection.createStatement()) {
            createLegacyUsers(statement);
            statement.execute("""
                    INSERT INTO users (username, password_hash, created_at) VALUES
                    ('Testus', 'hash1', now()), ('gammal-admin', 'hash2', now()), ('anna@example.com', 'hash3', now())""");

            statement.execute(Files.readString(MIGRATION));

            assertThat(count(statement, "select count(*) from users where email_verified = true")).isEqualTo(3);
            assertThat(count(statement, "select count(*) from users where email_verified = false")).isZero();
            assertThat(count(statement, "select count(*) from information_schema.columns where table_name = 'users' "
                    + "and column_name = 'email_verified' and is_nullable = 'NO' and column_default = 'false'"))
                    .isEqualTo(1);

            // Ett nytt konto är overifierat (false uttryckligen, som Hibernate sätter det).
            statement.execute("INSERT INTO users (username, password_hash, created_at, email_verified) "
                    + "VALUES ('ny@example.com', 'hash4', now(), false)");

            // Omkörning (schema.sql kör samma satser vid varje appstart) rör inte det kontot.
            statement.execute(Files.readString(MIGRATION));

            assertThat(count(statement,
                    "select count(*) from users where username = 'ny@example.com' and email_verified = false"))
                    .isEqualTo(1);
            assertThat(count(statement, "select count(*) from users where email_verified = true")).isEqualTo(3);
        }
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({"A_HIBERNATE_UK_OCH_PENDING, MIGRATION", "A_HIBERNATE_UK_OCH_PENDING, SCHEMA",
            "B_PARTIELLT_INDEX_OCH_DUBBLETTER, MIGRATION", "B_PARTIELLT_INDEX_OCH_DUBBLETTER, SCHEMA",
            "C_FÄRSK_DATABAS, MIGRATION", "C_FÄRSK_DATABAS, SCHEMA"})
    void skaFörvandlaGamlaUserTokensLägenTillEttUnikaConstraintUtanPendingKolumn(String state, String runner)
            throws Exception {
        String db = "migration_wine59_" + Math.abs((state + runner).hashCode());
        try (Connection connection = scratch(db); Statement statement = connection.createStatement()) {
            createLegacyUsers(statement);
            createTokenState(statement, state);

            for (int run = 0; run < 2; run++) {
                if (runner.equals("MIGRATION")) {
                    statement.execute(Files.readString(MIGRATION));
                } else {
                    runSchemaTokenPart(statement);
                }
            }

            assertThat(count(statement, "select count(*) from information_schema.columns "
                    + "where table_name = 'user_tokens' and column_name = 'pending_password_hash'")).isZero();
            assertThat(count(statement, "select count(*) from pg_indexes "
                    + "where indexname = 'user_tokens_one_reset_per_user'")).isZero();
            // Exakt ETT unikt constraint över (user_id, purpose), oavsett namn
            assertThat(count(statement, """
                    select count(*) from pg_constraint c
                    where c.conrelid = 'user_tokens'::regclass and c.contype = 'u'
                      and (select array_agg(a.attname::text order by a.attname::text)
                           from unnest(c.conkey) as k(attnum)
                           join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum)
                          = array['purpose', 'user_id']""")).isEqualTo(1);
            // ... som faktiskt hindrar dubbletter
            statement.execute("INSERT INTO user_tokens (user_id, purpose, token_hash, expires_at) "
                    + "VALUES (77, 'EMAIL_VERIFICATION', 'unik-ny-1', now())");
            assertThatThrownBy(() -> statement.execute("INSERT INTO user_tokens (user_id, purpose, token_hash, "
                    + "expires_at) VALUES (77, 'EMAIL_VERIFICATION', 'unik-ny-2', now())"))
                    .isInstanceOf(SQLException.class);
            if (state.startsWith("B_")) {
                // Dubbletterna rensades och det NYASTE behölls
                assertThat(count(statement, "select count(*) from user_tokens where user_id = 5 "
                        + "and purpose = 'EMAIL_VERIFICATION'")).isEqualTo(1);
                assertThat(count(statement, "select count(*) from user_tokens where token_hash = 'b-nyast'"))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void schemaSqlSkaBackfillaFöreNotNullPåSammaSättSomMigreringen() throws Exception {
        String schema = Files.readString(SCHEMA);
        int add = schema.indexOf("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified boolean;;");
        int backfill = schema.indexOf("UPDATE users SET email_verified = true WHERE email_verified IS NULL;;");
        int notNull = schema.indexOf("ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;;");
        assertThat(add).isPositive();
        assertThat(backfill).isGreaterThan(add);
        assertThat(notNull).isGreaterThan(backfill);
    }

    private static void runSchemaTokenPart(Statement statement) throws Exception {
        String schema = Files.readString(SCHEMA).replace("\r\n", "\n");
        String part = schema.substring(schema.indexOf("-- Ett token per användare och syfte"));
        for (String sql : part.split(";;")) {
            if (!sql.isBlank()) {
                statement.execute(sql);
            }
        }
    }

    private static void createTokenState(Statement statement, String state) throws SQLException {
        if (state.startsWith("C_")) {
            return;
        }
        statement.execute("""
                CREATE TABLE user_tokens (
                    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id bigint NOT NULL,
                    purpose varchar(255) NOT NULL,
                    token_hash varchar(255) NOT NULL,
                    expires_at timestamptz NOT NULL,
                    pending_password_hash varchar(255),
                    CONSTRAINT UK_hibernate_token_hash UNIQUE (token_hash)
                )""");
        if (state.startsWith("A_")) {
            statement.execute("ALTER TABLE user_tokens ADD CONSTRAINT UK_hibernate_style_pair "
                    + "UNIQUE (user_id, purpose)");
            statement.execute("INSERT INTO user_tokens (user_id, purpose, token_hash, expires_at, "
                    + "pending_password_hash) VALUES (5, 'EMAIL_VERIFICATION', 'a-1', now(), '{bcrypt}x')");
        } else {
            statement.execute("CREATE UNIQUE INDEX user_tokens_one_reset_per_user ON user_tokens (user_id) "
                    + "WHERE purpose = 'PASSWORD_RESET'");
            statement.execute("INSERT INTO user_tokens (user_id, purpose, token_hash, expires_at, "
                    + "pending_password_hash) VALUES (5, 'EMAIL_VERIFICATION', 'b-aldst', now(), '{bcrypt}1'), "
                    + "(5, 'EMAIL_VERIFICATION', 'b-mellan', now(), '{bcrypt}2'), "
                    + "(5, 'EMAIL_VERIFICATION', 'b-nyast', now(), '{bcrypt}3')");
        }
    }

    private static void createLegacyUsers(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE users (
                    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username varchar(255) NOT NULL UNIQUE,
                    password_hash varchar(255) NOT NULL,
                    created_at timestamptz NOT NULL,
                    default_min_quantity_filter integer NOT NULL DEFAULT 1,
                    own_rating_from_scale boolean NOT NULL DEFAULT false,
                    is_admin boolean NOT NULL DEFAULT false,
                    last_login_at timestamptz NOT NULL DEFAULT now()
                )""");
    }

    private static Connection scratch(String database) throws SQLException {
        try (Connection admin = POSTGRES.createConnection(""); Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database);
            statement.execute("CREATE DATABASE " + database);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int count(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
