package org.firstfolio.metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.security.InternalCallInterceptor;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsControllerTest {

    private static final String INTERNAL_TOKEN = "metrics-test-token";

    private PrometheusMeterRegistry registry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter("firstfolio.test.counter").increment();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricsController(registry))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(
                                ApiObjectMapperFactory.create()
                        )
                )
                .addFilters(new RequestIdFilter())
                .addMappedInterceptors(
                        new String[]{"/internal/**"},
                        new InternalCallInterceptor(INTERNAL_TOKEN)
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void exposesPrometheusTextWhenInternalTokenMatches() throws Exception {
        mockMvc.perform(get("/internal/metrics")
                        .header(
                                InternalCallInterceptor.INTERNAL_TOKEN_HEADER,
                                INTERNAL_TOKEN
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "firstfolio_test_counter_total 1.0"
                        )
                ));
    }

    @Test
    void rejectsScrapeWithoutInternalToken() throws Exception {
        mockMvc.perform(get("/internal/metrics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code")
                        .value("INTERNAL_CALL_REQUIRED"));
    }
}
