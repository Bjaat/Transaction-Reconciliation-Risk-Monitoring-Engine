package com.reconciliation.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Transaction Reconciliation & Risk Monitoring Engine.
 *
 * This is a modular monolith: a single deployable Spring Boot application,
 * internally organized into clear packages (controller, service, repository,
 * entity, dto, risk, reconciliation, etc.) rather than split into separate
 * services. This keeps the system simple to run and reason about while still
 * enforcing separation of concerns at the code level.
 */
@SpringBootApplication
public class ReconciliationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationEngineApplication.class, args);
    }
}
