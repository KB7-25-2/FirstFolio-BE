package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.quiz.domain.LevelTestChapterGradingResult;
import org.firstfolio.quiz.domain.LevelTestQuestionGradingResult;
import org.firstfolio.quiz.domain.LevelTestSubmitResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;

import java.util.List;

@Schema(description = "레벨 테스트 최종 제출 및 채점 결과")
public record LevelTestSubmitResponse(
        @Schema(description = "응시 ID", example = "2001") long attemptId,
        @Schema(description = "응시 상태", example = "GRADED") QuizAttemptStatus status,
        @Schema(description = "문항별 채점 결과") List<QuestionResultResponse> questionResults,
        @Schema(description = "대단원별 채점 요약") List<ChapterResultResponse> chapterResults,
        @Schema(description = "한 문항 이상 틀린 추천 대단원") List<RecommendationResponse> recommendations,
        @Schema(description = "모든 문항을 맞힌 추가 후보 대단원") List<CartCandidateResponse> cartCandidates
) {
    public LevelTestSubmitResponse {
        questionResults = List.copyOf(questionResults);
        chapterResults = List.copyOf(chapterResults);
        recommendations = List.copyOf(recommendations);
        cartCandidates = List.copyOf(cartCandidates);
    }

    public static LevelTestSubmitResponse from(LevelTestSubmitResult result) {
        List<ChapterResultResponse> chapters = result.chapterResults().stream()
                .map(ChapterResultResponse::from)
                .toList();
        return new LevelTestSubmitResponse(
                result.attemptId(),
                result.status(),
                result.questionResults().stream()
                        .map(QuestionResultResponse::from)
                        .toList(),
                chapters,
                result.chapterResults().stream()
                        .filter(chapter -> !chapter.allCorrect())
                        .map(RecommendationResponse::from)
                        .toList(),
                result.chapterResults().stream()
                        .filter(LevelTestChapterGradingResult::allCorrect)
                        .map(CartCandidateResponse::from)
                        .toList()
        );
    }

    @Schema(description = "문항별 채점 결과")
    public record QuestionResultResponse(
            @Schema(description = "문항 ID", example = "1001") long questionId,
            @Schema(description = "대단원 ID", example = "2") long mainChapterId,
            @Schema(description = "자산 유형", example = "DEPOSIT_SAVINGS") AssetType assetType,
            @Schema(description = "정답 여부", example = "false") boolean isCorrect
    ) {
        private static QuestionResultResponse from(
                LevelTestQuestionGradingResult result
        ) {
            return new QuestionResultResponse(
                    result.questionId(),
                    result.mainChapterId(),
                    result.assetType(),
                    result.correct()
            );
        }
    }

    @Schema(description = "대단원별 채점 요약")
    public record ChapterResultResponse(
            @Schema(description = "대단원 ID", example = "2") long mainChapterId,
            @Schema(description = "자산 유형", example = "DEPOSIT_SAVINGS") AssetType assetType,
            @Schema(description = "대단원 전체 문항 수", example = "3") int totalCount,
            @Schema(description = "대단원 정답 수", example = "2") int correctCount,
            @Schema(description = "대단원 전체 정답 여부", example = "false") boolean allCorrect
    ) {
        private static ChapterResultResponse from(
                LevelTestChapterGradingResult result
        ) {
            return new ChapterResultResponse(
                    result.mainChapterId(),
                    result.assetType(),
                    result.totalCount(),
                    result.correctCount(),
                    result.allCorrect()
            );
        }
    }

    @Schema(description = "레벨 테스트 오답 추천 대단원")
    public record RecommendationResponse(
            @Schema(description = "대단원 ID", example = "2") long mainChapterId,
            @Schema(description = "추천 출처", example = "LEVEL_TEST_WRONG") CurriculumSourceType sourceType
    ) {
        private static RecommendationResponse from(
                LevelTestChapterGradingResult result
        ) {
            return new RecommendationResponse(
                    result.mainChapterId(),
                    CurriculumSourceType.LEVEL_TEST_WRONG
            );
        }
    }

    @Schema(description = "레벨 테스트 전체 정답 추가 후보")
    public record CartCandidateResponse(
            @Schema(description = "대단원 ID", example = "3") long mainChapterId,
            @Schema(description = "자산 유형", example = "BOND") AssetType assetType
    ) {
        private static CartCandidateResponse from(
                LevelTestChapterGradingResult result
        ) {
            return new CartCandidateResponse(
                    result.mainChapterId(),
                    result.assetType()
            );
        }
    }
}
