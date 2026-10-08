package com.Reservation.Hotel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Sets up field encryption and upgrades an existing database:
 * 1. loads the AES key (app.crypto.key / CRYPTO_KEY) as soon as this bean is created,
 * 2. on MySQL, widens columns that now hold ciphertext (Hibernate "update" never alters column sizes),
 * 3. encrypts any personal values that are still stored as plain text.
 */
@Component
@Order(0)
public class DataProtectionConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataProtectionConfig.class);

    /** table -> encrypted columns */
    private static final Map<String, List<String>> ENCRYPTED_COLUMNS = Map.of(
            "users", List.of("phone"),
            "agent_profiles", List.of("phone"),
            "hotel_staff", List.of("phone", "email"),
            "payments", List.of("card_holder"),
            "inquiries", List.of("name", "email"),
            "vacation_requests", List.of("user_name", "user_email")
    );

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DataProtectionConfig(@Value("${app.crypto.key}") String key, JdbcTemplate jdbc, DataSource dataSource) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("No encryption key configured. Copy secrets.properties.example to "
                    + "src/main/resources/secrets.properties and set app.crypto.key (or set the CRYPTO_KEY environment variable).");
        }
        FieldEncryptor.init(key);
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean mysql;
        try (Connection c = dataSource.getConnection()) {
            mysql = c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        }
        int widened = 0, encrypted = 0;
        for (Map.Entry<String, List<String>> e : ENCRYPTED_COLUMNS.entrySet()) {
            String table = e.getKey();
            for (String col : e.getValue()) {
                if (mysql) widened += widenIfNeeded(table, col);
                encrypted += encryptPlainValues(table, col);
            }
        }
        if (widened + encrypted > 0) {
            log.info("Data protection: widened {} column(s), encrypted {} existing value(s)", widened, encrypted);
        }
    }

    private int widenIfNeeded(String table, String column) {
        List<Long> len = jdbc.queryForList("select CHARACTER_MAXIMUM_LENGTH from information_schema.COLUMNS "
                + "where TABLE_SCHEMA = database() and TABLE_NAME = ? and COLUMN_NAME = ?", Long.class, table, column);
        if (len.isEmpty() || len.get(0) == null || len.get(0) >= 255) return 0;
        jdbc.execute("alter table `" + table + "` modify `" + column + "` varchar(255)");
        log.info("Data protection: widened {}.{} from {} to 255 characters", table, column, len.get(0));
        return 1;
    }

    private int encryptPlainValues(String table, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, " + column + " as v from " + table
                + " where " + column + " is not null and " + column + " not like 'enc:v1:%'");
        for (Map<String, Object> row : rows) {
            jdbc.update("update " + table + " set " + column + " = ? where id = ?",
                    FieldEncryptor.encrypt(String.valueOf(row.get("v"))), row.get("id"));
        }
        return rows.size();
    }
}
