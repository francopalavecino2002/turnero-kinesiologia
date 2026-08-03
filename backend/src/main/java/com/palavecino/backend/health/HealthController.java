package com.palavecino.backend.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint for external uptime monitors (e.g. UptimeRobot). Deliberately does NOT touch
 * the database: its job is to prove "the app process is up and answering", which keeps the service
 * awake on free-tier hosting and raises the alarm when the whole app is down. A broken database
 * surfaces through the real API endpoints (500s) rather than through this endpoint - and making
 * this endpoint DB-dependent would produce false "DOWN" alerts whenever Neon free tier is paused
 * and resumes on demand. Spring MVC serves HEAD requests from this GET mapping automatically.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
