package org.firstfolio.config;

import org.firstfolio.auth.interceptor.FirebaseAuthenticationInterceptor;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.security.AdminAuthorizationInterceptor;
import org.firstfolio.common.security.InternalCallInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebMvc
@Import(OpenApiConfig.class)
@PropertySource("classpath:/application.properties")
@ComponentScan(
        basePackages = "org.firstfolio",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = {
                        Controller.class,
                        ControllerAdvice.class
                }
        )
)
public class ServletConfig implements WebMvcConfigurer {

    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    private final FirebaseAuthenticationInterceptor authenticationInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final String[] allowedOrigins;

    @Value("${internal.call-token:}")
    private String internalCallToken;

    public ServletConfig(
            FirebaseAuthenticationInterceptor authenticationInterceptor,
            CurrentUserArgumentResolver currentUserArgumentResolver,
            @Value("${cors.allowed-origins}") String allowedOrigins
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer servletPropertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter)
                        .setObjectMapper(ApiObjectMapperFactory.create());
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/health",
                        "/health",
                        "/api/internal/**",
                        "/internal/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/favicon.ico"
                );

        registry.addInterceptor(new AdminAuthorizationInterceptor())
                .addPathPatterns("/api/admin/**", "/admin/**");

        registry.addInterceptor(new InternalCallInterceptor(internalCallToken))
                .addPathPatterns("/api/internal/**", "/internal/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "Idempotency-Key",
                        "X-Request-Id"
                )
                .exposedHeaders("X-Request-Id")
                .allowCredentials(false)
                .maxAge(CORS_MAX_AGE_SECONDS);
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(currentUserArgumentResolver);
    }

    private static String[] parseAllowedOrigins(String configuredOrigins) {
        String[] origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        if (origins.length == 0) {
            throw new IllegalArgumentException("CORS allowed origins must not be empty.");
        }

        if (Arrays.asList(origins).contains("*")) {
            throw new IllegalArgumentException("Wildcard CORS origin is not allowed.");
        }

        return origins;
    }
}
