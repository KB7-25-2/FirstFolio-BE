package org.firstfolio.config;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsConfigTest {

    @Test
    void registersStandardJvmMetrics() {
        PrometheusMeterRegistry registry = new MetricsConfig()
                .prometheusMeterRegistry();

        try {
            assertNotNull(registry.find("jvm.memory.used").gauge());
            assertNotNull(registry.find("jvm.threads.live").gauge());
            assertNotNull(registry.find("process.uptime").timeGauge());
            assertTrue(registry.scrape().contains("jvm_memory_used_bytes"));
        } finally {
            registry.close();
        }
    }
}
