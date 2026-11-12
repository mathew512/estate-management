package com.kasa.estate_management;

import com.kasa.estate_management.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class EstateManagementApplicationTests extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void applicationStartsAndAllMigrationsAreApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT count(*) FROM flyway_schema_history WHERE success")) {
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(10);
        }
    }
}