package org.firstfolio.config;

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

    @Value("${internal.call-token:}")
    private String internalCallToken;

    @Bean
    public static PropertySourcesPlaceholderConfigurer servletPropertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    /**
     * 기본 컨버터 구성은 그대로 두고 JSON 컨버터의 ObjectMapper만 교체한다.
     * API_DOCS.md의 표기 규칙(snake_case, 금액 문자열, UTC 시각)은
     * {@link ApiObjectMapperFactory}에 모여 있다.
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter)
                        .setObjectMapper(ApiObjectMapperFactory.create());
            }
        }
    }

    /**
     * 권한 검증은 컨트롤러마다 반복하지 않고 경로 단위로 건다.
     * 새 관리자·내부 엔드포인트가 늘어도 검증을 빠뜨릴 수 없게 하기 위함이다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthorizationInterceptor())
                .addPathPatterns("/admin/**");

        registry.addInterceptor(new InternalCallInterceptor(internalCallToken))
                .addPathPatterns("/internal/**");
    }
}
