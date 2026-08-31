package com.reconciliation.engine.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Temporary Phase-1 controller used only to confirm that the Spring Boot
 * application context, embedded web server, and routing all work end to end
 * before any real domain logic is introduced.
 *
 * This will be removed once the real /api/transactions endpoints exist and
 * we can rely on those (or a proper /actuator/health check) for the same
 * purpose.
 */
@RestController
public class SystemStatusController {

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of(
                "service", "txn-reconciliation-engine",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
