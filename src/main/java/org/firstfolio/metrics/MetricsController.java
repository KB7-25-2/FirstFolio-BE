package org.firstfolio.metrics;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/metrics")
@Hidden
public class MetricsController {

    private static final MediaType PROMETHEUS_CONTENT_TYPE = MediaType.parseMediaType(
            "text/plain; version=0.0.4; charset=utf-8"
    );

    private final PrometheusMeterRegistry registry;

    public MetricsController(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(produces = "text/plain; version=0.0.4; charset=utf-8")
    public ResponseEntity<String> metrics() {
        return ResponseEntity.ok()
                .contentType(PROMETHEUS_CONTENT_TYPE)
                .body(registry.scrape());
    }
}
