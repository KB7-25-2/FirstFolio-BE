package org.firstfolio.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.filter.DelegatingFilterProxy;

import javax.servlet.Filter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WebConfigMetricsFilterTest {

    @Test
    void registersMetricsFilterProxyAfterRequestIdFilter() {
        Filter[] filters = new WebConfig().getServletFilters();

        assertEquals(3, filters.length);

        assertInstanceOf(
                DelegatingFilterProxy.class,
                filters[2]
        );
    }
}
