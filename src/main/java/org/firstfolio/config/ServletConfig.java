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
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebMvc
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

    private final FirebaseAuthenticationInterceptor authenticationInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Value("${internal.call-token:}")
    private String internalCallToken;

    public ServletConfig(
            FirebaseAuthenticationInterceptor authenticationInterceptor,
            CurrentUserArgumentResolver currentUserArgumentResolver
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
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
                        "/auth/**",
                        "/api/auth/**",
                        "/api/health",
                        "/health",
                        "/api/internal/**",
                        "/internal/**",
                        "/favicon.ico"
                );

        registry.addInterceptor(new AdminAuthorizationInterceptor())
                .addPathPatterns("/api/admin/**", "/admin/**");

        registry.addInterceptor(new InternalCallInterceptor(internalCallToken))
                .addPathPatterns("/api/internal/**", "/internal/**");
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(currentUserArgumentResolver);
    }
}
