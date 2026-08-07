package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("jdbc")
class JdbcConnectionIntegrationTest {

    @Test
    @DisplayName("애플리케이션 DataSource로 MySQL에 연결하고 SELECT 1을 실행한다")
    void connectsToMySqlThroughApplicationDataSource() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "jdbcConnectionIntegrationTest",
                            LocalDatabaseProperties.load()
                    )
            );
            context.register(RootConfig.class);
            context.refresh();

            DataSource dataSource = context.getBean(DataSource.class);

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertTrue(connection.isValid(5), "MySQL 연결이 유효해야 합니다.");
                assertTrue(resultSet.next(), "SELECT 1 결과가 존재해야 합니다.");
                assertEquals(1, resultSet.getInt(1));
            }
        }
    }
}
