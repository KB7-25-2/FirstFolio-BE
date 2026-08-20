package org.firstfolio.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.sql.DataSource;

/** HikariCP 2.x 풀 상태를 Micrometer gauge로 노출한다. */
public class HikariPoolMetricsBinder implements MeterBinder {

    private final HikariDataSource dataSource;

    public HikariPoolMetricsBinder(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource)) {
            throw new IllegalArgumentException(
                    "HikariPoolMetricsBinder requires a HikariDataSource"
            );
        }

        this.dataSource = (HikariDataSource) dataSource;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        String poolName = dataSource.getPoolName();

        poolGauge(
                registry,
                "hikaricp.connections.active",
                "Active connections in the pool",
                poolName,
                HikariPoolMXBean::getActiveConnections
        );
        poolGauge(
                registry,
                "hikaricp.connections.idle",
                "Idle connections in the pool",
                poolName,
                HikariPoolMXBean::getIdleConnections
        );
        poolGauge(
                registry,
                "hikaricp.connections.pending",
                "Threads waiting for a connection",
                poolName,
                HikariPoolMXBean::getThreadsAwaitingConnection
        );
        poolGauge(
                registry,
                "hikaricp.connections",
                "Total connections in the pool",
                poolName,
                HikariPoolMXBean::getTotalConnections
        );

        Gauge.builder(
                        "hikaricp.connections.max",
                        dataSource,
                        HikariDataSource::getMaximumPoolSize
                )
                .description("Maximum pool size")
                .tag("pool", poolName)
                .register(registry);

        Gauge.builder(
                        "hikaricp.connections.min",
                        dataSource,
                        HikariDataSource::getMinimumIdle
                )
                .description("Minimum idle connections")
                .tag("pool", poolName)
                .register(registry);
    }

    private void poolGauge(
            MeterRegistry registry,
            String name,
            String description,
            String poolName,
            PoolValue poolValue
    ) {
        Gauge.builder(name, dataSource, source -> readPoolValue(source, poolValue))
                .description(description)
                .tag("pool", poolName)
                .register(registry);
    }

    private static double readPoolValue(
            HikariDataSource dataSource,
            PoolValue poolValue
    ) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();

        return pool == null ? 0.0 : poolValue.read(pool);
    }

    @FunctionalInterface
    private interface PoolValue {
        int read(HikariPoolMXBean pool);
    }
}
