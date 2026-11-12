package com.kasa.estate_management.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Wipes the test database before the application starts, so Flyway rebuilds it from scratch.
 * It refuses to touch any database whose name does not end in "_test".
 */
public class TestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        String url = env.getProperty("spring.datasource.url", "");
        String user = env.getProperty("spring.datasource.username", "");
        String password = env.getProperty("spring.datasource.password", "");

        if (!url.endsWith("_test")) {
            throw new IllegalStateException(
                "Refusing to reset a database that is not a test database: " + url);
        }

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reset the test database " + url, e);
        }
    }
}