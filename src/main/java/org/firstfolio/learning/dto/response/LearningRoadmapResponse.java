package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.domain.LearningRoadmapStatus;
import org.firstfolio.quiz.domain.QuizAttemptStatus;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "사용자용 학습 로드맵 통합 응답")
public record LearningRoadmapResponse(
        List<Chapter> items
) {

    @Schema(description = "개인 커리큘럼 대단원과 학습 로드맵")
    public record Chapter(
            @Schema(description = "개인 커리큘럼 항목 ID", example = "501")
            long curriculumItemId,
            @Schema(description = "대단원 ID", example = "1")
            long mainChapterId,
            @Schema(description = "대단원명", example = "포트폴리오 기초")
            String title,
            @Schema(description = "대단원 설명", nullable = true)
            String description,
            @Schema(description = "대단원 유형", example = "FOUNDATION")
            ChapterType chapterType,
            @Schema(description = "개인 커리큘럼 표시 순서", example = "1")
            int displayOrder,
            @Schema(description = "로드맵의 대단원 상태", example = "IN_PROGRESS")
            LearningRoadmapStatus.Chapter status,
            @Schema(description = "대단원 최초 완료 일시", nullable = true)
            LocalDateTime completedAt,
            @Schema(description = "강좌와 퀴즈를 모두 마친 활성 소단원 완료율", example = "50")
            int progressPercent,
            @Schema(description = "활성 소단원 목록")
            List<SubChapter> subChapters,
            @Schema(description = "대단원 퀴즈 상태")
            MainChapterQuiz mainChapterQuiz
    ) {
    }

    @Schema(description = "학습 로드맵의 소단원과 사용자 진행 상태")
    public record SubChapter(
            @Schema(description = "소단원 ID", example = "101")
            long subChapterId,
            @Schema(description = "소단원명", example = "예금의 기초")
            String title,
            @Schema(description = "소단원 설명", nullable = true)
            String description,
            @Schema(description = "대단원 내 표시 순서", example = "1")
            int displayOrder,
            @Schema(description = "현재 공개 콘텐츠 버전 ID", nullable = true,
                    example = "301")
            Long currentContentVersionId,
            @Schema(description = "현재 공개 콘텐츠 존재 여부", example = "true")
            boolean contentAvailable,
            @Schema(description = "사용자 진도 ID", nullable = true,
                    example = "701")
            Long progressId,
            @Schema(description = "진도에 고정된 콘텐츠 버전 ID",
                    nullable = true, example = "301")
            Long progressContentVersionId,
            @Schema(description = "저장된 학습 상태", example = "IN_PROGRESS")
            LearningProgressStatus progressStatus,
            @Schema(description = "마지막 학습 페이지 ID", nullable = true,
                    example = "page-2")
            String lastPageId,
            @Schema(description = "최초 학습 시작 일시", nullable = true)
            LocalDateTime startedAt,
            @Schema(description = "최초 학습 완료 일시", nullable = true)
            LocalDateTime completedAt,
            @Schema(description = "마지막 진도 갱신 일시", nullable = true)
            LocalDateTime updatedAt,
            @Schema(description = "소단원 퀴즈 완료·이어풀기 상태")
            SubChapterQuiz quiz,
            @Schema(description = "로드맵 표시·접근 상태", example = "NEXT")
            LearningRoadmapStatus.Schedule scheduleStatus
    ) {
    }

    @Schema(description = "소단원 퀴즈 완료·이어풀기 상태")
    public record SubChapterQuiz(
            @Schema(description = "최초 소단원 퀴즈 완료 여부", example = "false")
            boolean completed,
            @Schema(description = "현재 진행 중인 응시 ID", nullable = true,
                    example = "3001")
            Long activeAttemptId,
            @Schema(description = "현재 진행 중인 응시의 답변 수", example = "1")
            int answeredCount,
            @Schema(description = "현재 진행 중인 응시의 전체 문항 수", example = "3")
            int totalCount
    ) {
    }

    @Schema(description = "대단원 퀴즈의 로드맵 상태")
    public record MainChapterQuiz(
            @Schema(description = "현재 공개된 대단원 퀴즈 문항 존재 여부",
                    example = "true")
            boolean questionAvailable,
            @Schema(description = "현재 대단원 퀴즈 시작·이어하기 가능 여부",
                    example = "false")
            boolean available,
            @Schema(description = "로드맵 표시 상태", example = "LOCKED")
            LearningRoadmapStatus.Quiz status,
            @Schema(description = "가장 최근 대단원 퀴즈 응시 ID",
                    nullable = true, example = "801")
            Long attemptId,
            @Schema(description = "가장 최근 응시 상태", nullable = true,
                    example = "IN_PROGRESS")
            QuizAttemptStatus attemptStatus,
            @Schema(description = "가장 최근 응시의 전체 문항 수",
                    nullable = true, example = "3")
            Integer totalCount,
            @Schema(description = "가장 최근 응시의 정답 수",
                    nullable = true, example = "2")
            Integer correctCount,
            @Schema(description = "가장 최근 응시 점수", nullable = true,
                    example = "67")
            Integer score,
            @Schema(description = "가장 최근 응시 시작 일시", nullable = true)
            LocalDateTime startedAt,
            @Schema(description = "가장 최근 응시 완료 일시", nullable = true)
            LocalDateTime submittedAt
    ) {
    }

}
