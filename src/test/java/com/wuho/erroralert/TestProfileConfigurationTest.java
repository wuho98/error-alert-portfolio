package com.wuho.erroralert;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TestProfileConfigurationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Test
    void testProfileUsesH2InMemoryDatabase() throws Exception {
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).contains("test");
        assertThat(environment.getProperty("spring.datasource.url"))
                .contains("jdbc:h2:mem:error_alert")
                .contains("MODE=MySQL");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.redis.enabled", Boolean.class)).isFalse();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select 1")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).contains("H2");
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}
