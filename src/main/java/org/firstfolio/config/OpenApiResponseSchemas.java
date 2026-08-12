package org.firstfolio.config;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.admin.dto.response.AdminProductPageResponse;
import org.firstfolio.admin.dto.response.AdminProductResponse;
import org.firstfolio.admin.dto.response.ProductImportResponse;
import org.firstfolio.auth.dto.response.LoginResponse;
import org.firstfolio.auth.dto.response.SignupResponse;
import org.firstfolio.content.dto.response.ContentVersionCreateResponse;
import org.firstfolio.dashboard.dto.response.DashboardResponse;
import org.firstfolio.content.dto.response.ContentVersionListResponse;
import org.firstfolio.content.dto.response.ContentVersionPublishResponse;
import org.firstfolio.curriculum.dto.response.MainChapterCreateResponse;
import org.firstfolio.curriculum.dto.response.MainChapterListResponse;
import org.firstfolio.curriculum.dto.response.MainChapterPatchResponse;
import org.firstfolio.curriculum.dto.response.SubChapterCreateResponse;
import org.firstfolio.curriculum.dto.response.SubChapterListResponse;
import org.firstfolio.curriculum.dto.response.SubChapterPatchResponse;
import org.firstfolio.learning.dto.response.LessonContentResponse;
import org.firstfolio.learning.dto.response.LearningProgressResponse;
import org.firstfolio.learning.dto.response.LearningProgressUpdateResponse;
import org.firstfolio.learning.dto.response.LearningContinueResponse;
import org.firstfolio.learning.dto.response.PublicMainChapterListResponse;
import org.firstfolio.learning.dto.response.PublicSubChapterListResponse;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioEventProcessResponse;
import org.firstfolio.portfolio.dto.response.PortfolioEventRetryResponse;
import org.firstfolio.portfolio.dto.response.PortfolioResetResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.dto.response.TradeResponse;
import org.firstfolio.quiz.dto.response.QuizQuestionCreateResponse;
import org.firstfolio.quiz.dto.response.QuizAttemptStartResponse;
import org.firstfolio.quiz.dto.response.QuizAnswerGradingResponse;
import org.firstfolio.simulation.dto.response.PriceRefreshResponse;
import org.firstfolio.simulation.dto.response.ProductDetailResponse;
import org.firstfolio.simulation.dto.response.ProductPageResponse;
import org.firstfolio.user.dto.response.PointBalanceResponse;
import org.firstfolio.user.dto.response.UserProfilePatchResponse;
import org.firstfolio.user.dto.response.UserProfileResponse;

/**
 * Swagger에서 공통 {@code {"data": ...}} 응답 구조의 실제 제네릭 타입을 보존하기 위한 문서 전용 스키마다.
 * 런타임 응답 생성에는 {@code ApiResponse<T>}를 그대로 사용한다.
 */
public final class OpenApiResponseSchemas {

    private OpenApiResponseSchemas() {
    }

    @Schema(name = "SignupApiResponse")
    public record Signup(SignupResponse data) { }

    @Schema(name = "LoginApiResponse")
    public record Login(LoginResponse data) { }

    @Schema(name = "UserProfileApiResponse")
    public record UserProfile(UserProfileResponse data) { }

    @Schema(name = "UserProfilePatchApiResponse")
    public record UserProfilePatch(UserProfilePatchResponse data) { }

    @Schema(name = "PointBalanceApiResponse")
    public record PointBalance(PointBalanceResponse data) { }

    @Schema(name = "LessonContentApiResponse")
    public record LessonContent(LessonContentResponse data) { }

    @Schema(name = "LearningProgressApiResponse")
    public record LearningProgress(LearningProgressResponse data) { }

    @Schema(name = "LearningProgressUpdateApiResponse")
    public record LearningProgressUpdate(LearningProgressUpdateResponse data) { }

    @Schema(name = "LearningContinueApiResponse")
    public record LearningContinue(LearningContinueResponse data) { }

    @Schema(name = "PublicMainChapterListApiResponse")
    public record PublicMainChapterList(PublicMainChapterListResponse data) { }

    @Schema(name = "PublicSubChapterListApiResponse")
    public record PublicSubChapterList(PublicSubChapterListResponse data) { }

    @Schema(name = "MainChapterListApiResponse")
    public record MainChapterList(MainChapterListResponse data) { }

    @Schema(name = "MainChapterCreateApiResponse")
    public record MainChapterCreate(MainChapterCreateResponse data) { }

    @Schema(name = "MainChapterPatchApiResponse")
    public record MainChapterPatch(MainChapterPatchResponse data) { }

    @Schema(name = "SubChapterListApiResponse")
    public record SubChapterList(SubChapterListResponse data) { }

    @Schema(name = "SubChapterCreateApiResponse")
    public record SubChapterCreate(SubChapterCreateResponse data) { }

    @Schema(name = "SubChapterPatchApiResponse")
    public record SubChapterPatch(SubChapterPatchResponse data) { }

    @Schema(name = "ContentVersionListApiResponse")
    public record ContentVersionList(ContentVersionListResponse data) { }

    @Schema(name = "ContentVersionCreateApiResponse")
    public record ContentVersionCreate(ContentVersionCreateResponse data) { }

    @Schema(name = "ContentVersionPublishApiResponse")
    public record ContentVersionPublish(ContentVersionPublishResponse data) { }

    @Schema(name = "QuizQuestionCreateApiResponse")
    public record QuizQuestionCreate(QuizQuestionCreateResponse data) { }

    @Schema(name = "QuizAttemptStartApiResponse")
    public record QuizAttemptStart(QuizAttemptStartResponse data) { }

    @Schema(name = "QuizAnswerGradingApiResponse")
    public record QuizAnswerGrading(QuizAnswerGradingResponse data) { }

    @Schema(name = "PortfolioDetailApiResponse")
    public record PortfolioDetail(PortfolioDetailResponse data) { }

    @Schema(name = "PortfolioTransactionPageApiResponse")
    public record PortfolioTransactions(PortfolioTransactionPageResponse data) { }

    @Schema(name = "TradeApiResponse")
    public record Trade(TradeResponse data) { }

    @Schema(name = "PortfolioResetApiResponse")
    public record PortfolioReset(PortfolioResetResponse data) { }

    @Schema(name = "PortfolioEventProcessApiResponse")
    public record PortfolioEventProcess(PortfolioEventProcessResponse data) { }

    @Schema(name = "PortfolioEventRetryApiResponse")
    public record PortfolioEventRetry(PortfolioEventRetryResponse data) { }

    @Schema(name = "ProductPageApiResponse")
    public record ProductPage(ProductPageResponse data) { }

    @Schema(name = "ProductDetailApiResponse")
    public record ProductDetail(ProductDetailResponse data) { }

    @Schema(name = "PriceRefreshApiResponse")
    public record PriceRefresh(PriceRefreshResponse data) { }

    @Schema(name = "ProductImportApiResponse")
    public record ProductImport(ProductImportResponse data) { }

    @Schema(name = "AdminProductPageApiResponse")
    public record AdminProductPage(AdminProductPageResponse data) { }

    @Schema(name = "AdminProductApiResponse")
    public record AdminProduct(AdminProductResponse data) { }

    @Schema(name = "DashboardApiResponse")
    public record Dashboard(DashboardResponse data) { }
}
