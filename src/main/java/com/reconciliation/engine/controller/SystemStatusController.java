package com.reconciliation.engine.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight application liveness endpoint. It deliberately performs no
 * domain or database work, allowing callers to confirm that the web process
 * is responsive without coupling health checks to a business API.
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
