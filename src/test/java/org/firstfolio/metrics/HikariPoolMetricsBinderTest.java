package org.firstfolio.metrics;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HikariPoolMetricsBinderTest {

    @Test
    void exposesPoolSizeAndSafeZeroBeforePoolStarts() {
        HikariDataSource dataSource = new HikariDataSource();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        dataSource.setPoolName("TestPool");
        dataSource.setMaximumPoolSize(7);
        dataSource.setMinimumIdle(2);

        new HikariPoolMetricsBinder(dataSource).bindTo(registry);

        assertEquals(
                7.0,
                registry.get("hikaricp.connections.max")
                        .tag("pool", "TestPool")
                        .gauge()
                        .value()
        );
        assertEquals(
                2.0,
                registry.get("hikaricp.connections.min")
                        .tag("pool", "TestPool")
                        .gauge()
                        .value()
        );
        assertEquals(
                0.0,
                registry.get("hikaricp.connections.active")
                        .tag("pool", "TestPool")
                        .gauge()
                        .value()
        );

        registry.close();
        dataSource.close();
    }
}
