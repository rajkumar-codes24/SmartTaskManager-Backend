package com.smarttaskmanager.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_hibernate_validate_db;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=4c696e676572696e675f73757065725f7365637265745f6b65795f666f725f736d6172745f7461736b5f6d616e616765725f3230323621",
        "management.health.mail.enabled=false",
        "spring.mail.host=smtp.mailtrap.io",
        "spring.mail.port=2525",
        "spring.mail.username=test",
        "spring.mail.password=test",
        "app.mail.from=test@smarttaskmanager.com",
        "app.mail.from-name=Smart Task Manager Test",
        "app.public-base-url=http://localhost:8080",
        "app.frontend-base-url=http://localhost:3000",
        "app.reminder.scheduler-interval-ms=300000",
        "app.reminder.max-batch-size=50",
        "app.reminder.max-retries=3"
})
class FlywayHibernateValidationIntegrationTest {

    @Test
    @DisplayName("Flyway executes migrations V1-V5 and Hibernate validates all entities with ddl-auto=validate")
    void testFlywayMigratesAndHibernateValidatesSuccessfully() {
        // If this test passes, Spring Boot successfully executed Flyway migrations on an empty database
        // and Hibernate schema validation succeeded against the newly generated tables.
    }
}
