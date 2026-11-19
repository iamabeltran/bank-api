package com.portfolio.bankapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.portfolio.bankapi.support.IntegrationTest;

/**
 * Prueba base de infraestructura: con un MySQL REAL, Flyway aplica las migraciones
 * y verificamos que el esquema funciona.
 */
@IntegrationTest
class MySqlIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesTables() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN ('accounts', 'transactions')",
                Integer.class);
        assertThat(tables).isEqualTo(2);
    }

    @Test
    void databaseRejectsNegativeBalance() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (owner_name, balance, currency) VALUES ('Ana', -1, 'MXN')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_accounts_balance_non_negative");
    }
}
