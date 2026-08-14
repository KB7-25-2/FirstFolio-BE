package org.firstfolio.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.response.ErrorResponse;
import org.firstfolio.common.security.InternalCallInterceptor;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocConfiguration;
import org.springdoc.core.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.SpringDocUIConfiguration;
import org.springdoc.core.SpringDocUtils;
import org.springdoc.core.SwaggerUiConfigParameters;
import org.springdoc.core.SwaggerUiConfigProperties;
import org.springdoc.core.SwaggerUiOAuthProperties;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springdoc.webmvc.core.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocSpecPropertiesConfiguration.class,
        SpringDocWebMvcConfiguration.class,
        MultipleOpenApiSupportConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiConfigParameters.class,
        SpringDocUIConfiguration.class,
        SwaggerUiOAuthProperties.class
})
public class OpenApiConfig {

    static final String FIREBASE_BEARER_SCHEME = "firebaseBearer";
    static final String INTERNAL_CALL_SCHEME = "internalCallToken";

    static {
        // @CurrentUser로 주입되는 서버 내부 사용자 정보는 API 입력값이 아니다.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedUser.class);
    }

    @Bean
    public OpenAPI firstFolioOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(
                        FIREBASE_BEARER_SCHEME,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Firebase ID Token")
                                .description("Firebase ID Token을 입력합니다.")
                )
                .addSecuritySchemes(
                        INTERNAL_CALL_SCHEME,
                        new SecurityScheme()
                                .name(InternalCallInterceptor.INTERNAL_TOKEN_HEADER)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("내부 배치·스케줄러 호출 토큰입니다.")
                );

        return new OpenAPI()
                .info(new Info()
                        .title("FirstFolio API")
                        .description("FirstFolio 금융 학습 및 모의 자산 관리 API")
                        .version("v1"))
                .components(components);
    }

    /**
     * 런타임 API와 OpenAPI가 같은 JSON 필드명 규칙을 사용하게 한다.
     *
     * <p>Spring MVC는 {@link ApiObjectMapperFactory}의 {@code snake_case} ObjectMapper로
     * 요청과 응답을 변환한다. Swagger의 기본 ModelResolver는 별도 ObjectMapper를 사용하므로
     * 이 Bean이 없으면 실제 JSON은 {@code snake_case}, 문서 스키마는 {@code camelCase}가 될 수 있다.</p>
     */
    @Bean
    public ModelResolver apiModelResolver() {
        return new ModelResolver(ApiObjectMapperFactory.create());
    }

    @Bean
    public OpenApiCustomiser apiSecurityCustomiser() {
        return openApi -> {
            ModelConverters.getInstance()
                    .readAll(ErrorResponse.class)
                    .forEach(openApi.getComponents()::addSchemas);

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                String securityScheme = securitySchemeFor(path);

                if (securityScheme == null) {
                    return;
                }

                pathItem.readOperations().forEach(operation -> {
                    operation.addSecurityItem(
                            new SecurityRequirement().addList(securityScheme)
                    );
                    operation.getResponses().forEach((status, response) -> {
                        if (isErrorStatus(status)) {
                            response.setContent(new Content().addMediaType(
                                    "application/json",
                                    new MediaType().schema(
                                            new Schema<>().$ref("#/components/schemas/ErrorResponse")
                                    )
                            ));
                        }
                    });
                });
            });
        };
    }

    private static boolean isErrorStatus(String status) {
        return status != null
                && status.length() == 3
                && (status.charAt(0) == '4' || status.charAt(0) == '5');
    }

    private static String securitySchemeFor(String path) {
        if (path.equals("/api/internal")
                || path.startsWith("/api/internal/")
                || path.equals("/internal")
                || path.startsWith("/internal/")) {
            return INTERNAL_CALL_SCHEME;
        }

        if (path.equals("/api/health")
                || path.equals("/health")) {
            return null;
        }

        return FIREBASE_BEARER_SCHEME;
    }
}
