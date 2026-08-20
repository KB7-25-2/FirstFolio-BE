package org.firstfolio.config;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadDeadlockMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.firstfolio.metrics.HikariPoolMetricsBinder;
import org.firstfolio.metrics.HttpRequestMetricsFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MetricsConfig {

    @Bean(destroyMethod = "close")
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT
        );

        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new JvmThreadDeadlockMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new FileDescriptorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);

        return registry;
    }

    @Bean
    public HikariPoolMetricsBinder hikariPoolMetricsBinder(
            DataSource dataSource,
            PrometheusMeterRegistry registry
    ) {
        HikariPoolMetricsBinder binder = new HikariPoolMetricsBinder(dataSource);

        binder.bindTo(registry);
        return binder;
    }

    @Bean(name = HttpRequestMetricsFilter.BEAN_NAME)
    public HttpRequestMetricsFilter httpRequestMetricsFilter(
            PrometheusMeterRegistry registry
    ) {
        return new HttpRequestMetricsFilter(registry);
    }
}
