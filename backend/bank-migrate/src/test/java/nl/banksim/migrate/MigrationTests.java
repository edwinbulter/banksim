package nl.banksim.migrate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class MigrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine")
            .withInitScript("roles.sql");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sessietabellenBestaanInSchemaBff() {
        assertThat(jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'bff' order by table_name",
                String.class))
                .containsExactly("spring_session", "spring_session_attributes");
    }

    @Test
    void bankBffMagAlleenSessiesLezenEnSchrijven() {
        assertThat(jdbc.queryForObject(
                "select has_table_privilege('bank_bff', 'bff.spring_session', 'INSERT,SELECT,UPDATE,DELETE')",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "select has_schema_privilege('bank_bff', 'bff', 'CREATE')", Boolean.class)).isFalse();
    }
}
