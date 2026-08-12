package org.firstfolio.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 다른 5개 섹션을 조합한 최종 대시보드 집계 섹션
@Schema(description = "대시보드 5개 섹션 집계 응답")
public final class DashboardResponse {

    @Schema(description = "포트폴리오 요약")
    private final PortfolioSummaryResponse portfolio;
    @Schema(description = "일일 퀘스트 요약")
    private final DailyQuestSummaryResponse dailyQuest;
    @Schema(description = "학습 이어하기 요약")
    private final LearningSummaryResponse learning;
    @Schema(description = "예정 자산 이벤트 목록")
    private final List<UpcomingEventResponse> upcomingEvents;
    @Schema(description = "최신 뉴스 목록")
    private final List<LatestNewsResponse> latestNews;

    public DashboardResponse(
        PortfolioSummaryResponse portfolio,
        DailyQuestSummaryResponse dailyQuest,
        LearningSummaryResponse learning,
        List<UpcomingEventResponse> upcomingEvents,
        List<LatestNewsResponse> latestNews
    ) {
        this.portfolio = portfolio;
        this.dailyQuest = dailyQuest;
        this.learning = learning;
        this.upcomingEvents = upcomingEvents;
        this.latestNews = latestNews;
    }

    public PortfolioSummaryResponse getPortfolio() {
        return portfolio;
    }

    public DailyQuestSummaryResponse getDailyQuest() {
        return dailyQuest;
    }

    public LearningSummaryResponse getLearning() {
        return learning;
    }

    public List<UpcomingEventResponse> getUpcomingEvents() {
        return upcomingEvents;
    }

    public List<LatestNewsResponse> getLatestNews() {
        return latestNews;
    }
}
