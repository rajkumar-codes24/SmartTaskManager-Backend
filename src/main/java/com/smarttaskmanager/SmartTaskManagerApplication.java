package com.smarttaskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Smart Task Manager backend application.
 *
 * <p>Configured with {@link ConfigurationPropertiesScan} to automatically
 * bind strongly-typed configuration classes, {@link EnableScheduling}
 * to support background reminder dispatching, and {@link EnableJpaAuditing}
 * for automated entity timestamp tracking.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableJpaAuditing
public class SmartTaskManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartTaskManagerApplication.class, args);
    }
}
