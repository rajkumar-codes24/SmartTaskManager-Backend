package com.smarttaskmanager.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTests {

    @Test
    @DisplayName("All Flyway migration scripts execute cleanly and create expected schema")
    void testFlywayMigrationsExecuteSuccessfully() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        int appliedCount = flyway.migrate().migrationsExecuted;

        assertThat(appliedCount).isEqualTo(14);

        MigrationInfo[] appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations).hasSize(14);
        assertThat(appliedMigrations[0].getDescription()).isEqualTo("create users table");
        assertThat(appliedMigrations[1].getDescription()).isEqualTo("create tokens tables");
        assertThat(appliedMigrations[2].getDescription()).isEqualTo("create categories table");
        assertThat(appliedMigrations[3].getDescription()).isEqualTo("create tasks table");
        assertThat(appliedMigrations[4].getDescription()).isEqualTo("create reminders table");
        assertThat(appliedMigrations[5].getDescription()).isEqualTo("add task scheduling fields");
        assertThat(appliedMigrations[6].getDescription()).isEqualTo("create user scheduling preferences table");
        assertThat(appliedMigrations[7].getDescription()).isEqualTo("create subtasks table");
        assertThat(appliedMigrations[8].getDescription()).isEqualTo("create task recurrence rules table");
        assertThat(appliedMigrations[9].getDescription()).isEqualTo("create labels and task labels tables");
        assertThat(appliedMigrations[10].getDescription()).isEqualTo("create task dependencies table");
        assertThat(appliedMigrations[11].getDescription()).isEqualTo("create productivity goals table");
        assertThat(appliedMigrations[12].getDescription()).isEqualTo("add generated occurrences to recurrence rules");
        assertThat(appliedMigrations[13].getDescription()).isEqualTo("add version and completed to recurrence rules");

        // Verify that all new tables exist
        Set<String> tableNames = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }

        assertThat(tableNames).contains(
                "users",
                "refresh_tokens",
                "email_verification_tokens",
                "password_reset_tokens",
                "categories",
                "tasks",
                "reminders",
                "user_scheduling_preferences",
                "subtasks",
                "task_recurrence_rules",
                "labels",
                "task_labels",
                "task_dependencies",
                "productivity_goals",
                "flyway_schema_history"
        );
    }
}
