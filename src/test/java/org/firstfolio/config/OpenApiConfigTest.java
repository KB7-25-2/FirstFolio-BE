package org.firstfolio.config;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.controller.AuthController;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.service.AuthUseCase;
import org.firstfolio.learning.controller.LessonContentController;
import org.firstfolio.learning.controller.LearningProgressController;
import org.firstfolio.learning.controller.PublicChapterController;
import org.firstfolio.learning.service.LessonContentQueryService;
import org.firstfolio.learning.service.LearningProgressService;
import org.firstfolio.learning.service.PublicChapterQueryService;
import org.firstfolio.portfolio.controller.PortfolioController;
import org.firstfolio.portfolio.service.PortfolioQueryService;
import org.firstfolio.portfolio.service.PortfolioResetService;
import org.firstfolio.portfolio.service.TradeService;
import org.firstfolio.quiz.controller.QuizAttemptController;
import org.firstfolio.quiz.controller.QuizAnswerController;
import org.firstfolio.quiz.service.QuizAnswerGradingService;
import org.firstfolio.quiz.service.QuizAttemptStartService;
import org.firstfolio.simulation.controller.InternalProductPriceController;
import org.firstfolio.simulation.service.PriceRefreshService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class OpenApiConfigTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void exposesOpenApiDocumentWithFirebaseSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.info.title").value("FirstFolio API"))
                .andExpect(jsonPath("$.paths['/api/swagger-test'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/swagger-test'].get.parameters"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/swagger-test'].get.security[0].firebaseBearer"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/swagger-test'].get.security[0].firebaseBearer"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/internal/swagger-test'].get.security[0].internalCallToken"
                ).isArray())
                .andExpect(jsonPath(
                        "$.components.securitySchemes.firebaseBearer.scheme"
                ).value("bearer"));
    }

    @Test
    void exposesDescriptionsFromDomainSpecifications() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/signup'].post.summary"
                ).value("회원가입"))
                .andExpect(jsonPath(
                        "$.paths['/api/auth/signup'].post.responses['409'].description"
                ).value(org.hamcrest.Matchers.containsString("ACCOUNT_CONFLICT")))
                .andExpect(jsonPath(
                        "$.paths['/api/auth/signup'].post.responses['409'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.ErrorResponse.properties.error['$ref']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}'].get.responses['404'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}'].get.responses['503'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/internal/product-prices/refresh'].post.responses['403'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/internal/product-prices/refresh'].post.responses['422'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/auth/signup'].post.responses['201'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/SignupApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/portfolios/current'].get.summary"
                ).value("현재 포트폴리오 조회"))
                .andExpect(jsonPath(
                        "$.paths['/api/portfolios/current/trades'].post.responses['422'].description"
                ).value(org.hamcrest.Matchers.containsString("TRADE_NOT_ALLOWED")))
                .andExpect(jsonPath(
                        "$.paths['/api/portfolios/current'].get.responses['200'].content['application/json'].schema"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}'].get.parameters[0].description"
                ).value("조회할 소단원 ID"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/main-chapters'].get.responses['200'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/PublicMainChapterListApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/main-chapters/{mainChapterId}/sub-chapters'].get.responses['200'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/PublicSubChapterListApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/main-chapters/{mainChapterId}/sub-chapters'].get.responses['404'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}/progress'].put.responses['200'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/LearningProgressUpdateApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}/progress'].post"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}/progress'].get.responses['200'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/LearningProgressApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}/progress'].get.responses['404'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}/quiz-attempts'].post.responses['201'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/QuizAttemptStartApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/sub-chapters/{subChapterId}/quiz-attempts'].post.responses['403'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/quiz-attempts/{attemptId}/answers/{questionId}'].put.responses['200'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/QuizAnswerGradingApiResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/learning/quiz-attempts/{attemptId}/answers/{questionId}'].put.responses['409'].content['application/json'].schema['$ref']"
                ).value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.SignupRequest.properties.nickname.description"
                ).value("2~10자의 서비스 닉네임"))
                .andExpect(jsonPath(
                        "$.components.schemas.SignupApiResponse.properties.data['$ref']"
                ).value("#/components/schemas/SignupResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.TradeRequest.properties.amount.type"
                ).value("string"));
    }

    @Test
    void redirectsSwaggerUiEntryPoint() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }

    @Configuration
    @EnableWebMvc
    @Import(OpenApiConfig.class)
    static class TestWebConfig {

        @Bean
        SwaggerTestController swaggerTestController() {
            return new SwaggerTestController();
        }

        @Bean
        PublicSwaggerTestController publicSwaggerTestController() {
            return new PublicSwaggerTestController();
        }

        @Bean
        InternalSwaggerTestController internalSwaggerTestController() {
            return new InternalSwaggerTestController();
        }

        @Bean
        AuthController authController() {
            return new AuthController(mock(AuthUseCase.class));
        }

        @Bean
        PortfolioController portfolioController() {
            return new PortfolioController(
                    mock(PortfolioQueryService.class),
                    mock(PortfolioResetService.class),
                    mock(TradeService.class)
            );
        }

        @Bean
        LessonContentController lessonContentController() {
            return new LessonContentController(mock(LessonContentQueryService.class));
        }

        @Bean
        PublicChapterController publicChapterController() {
            return new PublicChapterController(mock(PublicChapterQueryService.class));
        }

        @Bean
        LearningProgressController learningProgressController() {
            return new LearningProgressController(mock(LearningProgressService.class));
        }

        @Bean
        QuizAttemptController quizAttemptController() {
            return new QuizAttemptController(mock(QuizAttemptStartService.class));
        }

        @Bean
        QuizAnswerController quizAnswerController() {
            return new QuizAnswerController(mock(QuizAnswerGradingService.class));
        }

        @Bean
        InternalProductPriceController internalProductPriceController() {
            return new InternalProductPriceController(mock(PriceRefreshService.class));
        }
    }

    @RestController
    @RequestMapping("/api/swagger-test")
    static class SwaggerTestController {

        @GetMapping
        String get(@CurrentUser AuthenticatedUser currentUser) {
            return "ok";
        }
    }

    @RestController
    @RequestMapping("/api/auth/swagger-test")
    static class PublicSwaggerTestController {

        @GetMapping
        String get() {
            return "ok";
        }
    }

    @RestController
    @RequestMapping("/api/internal/swagger-test")
    static class InternalSwaggerTestController {

        @GetMapping
        String get() {
            return "ok";
        }
    }
}
