package org.firstfolio.dailyquest.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.dailyquest.domain.DailyQuestItemGradingResult;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.domain.DailyQuestSubmitResult;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "일일 퀘스트 최종 제출·채점 결과")
public record DailyQuestSubmitResponse(
        @Schema(description = "일일 퀘스트 ID", example = "4001") long dailyQuestId,
        @Schema(description = "완료 상태", example = "COMPLETED") DailyQuestStatus status,
        @Schema(description = "정답 수", example = "4") int correctCount,
        @Schema(description = "리더보드 반영 점수", example = "4") int score,
        @Schema(description = "포인트 보상 결과") RewardResponse reward,
        @Schema(description = "문항별 채점 결과") List<ItemResultResponse> results,
        @Schema(description = "최종 제출 시각") LocalDateTime completedAt
) {
    public DailyQuestSubmitResponse {
        results = List.copyOf(results);
    }

    public static DailyQuestSubmitResponse from(DailyQuestSubmitResult result) {
        return new DailyQuestSubmitResponse(
                result.dailyQuestId(),
                result.status(),
                result.correctCount(),
                result.score(),
                new RewardResponse(
                        result.reward().points(),
                        result.reward().pointTransactionId()
                ),
                result.results().stream()
                        .map(ItemResultResponse::from)
                        .toList(),
                result.completedAt()
        );
    }

    @Schema(description = "일일 퀘스트 포인트 보상")
    public record RewardResponse(
            @Schema(description = "지급 포인트", example = "400") int points,
            @Schema(
                    description = "포인트 원장 ID. 0포인트면 null",
                    example = "9001",
                    nullable = true
            )
            Long pointTransactionId
    ) {
    }

    @Schema(description = "일일 퀘스트 문항별 채점 결과")
    public record ItemResultResponse(
            @Schema(description = "일일 퀘스트 문항 배정 ID", example = "5001") long dailyQuestItemId,
            @Schema(description = "문항 버전 ID", example = "1001") long questionId,
            @Schema(description = "정답 여부", example = "true") boolean isCorrect,
            @Schema(description = "최종 제출 답안") AnswerResponse submittedAnswer,
            @Schema(description = "정답") AnswerResponse correctAnswer,
            @Schema(description = "정답 해설") String explanation,
            @Schema(
                    description = "AI 문항의 근거 출처와 기준 시점. 일반 문항은 null",
                    nullable = true
            )
            JsonNode sourceRefs
    ) {
        public ItemResultResponse {
            sourceRefs = sourceRefs == null ? null : sourceRefs.deepCopy();
        }

        private static ItemResultResponse from(
                DailyQuestItemGradingResult result
        ) {
            return new ItemResultResponse(
                    result.dailyQuestItemId(),
                    result.questionId(),
                    result.correct(),
                    new AnswerResponse(result.submittedAnswerKey()),
                    new AnswerResponse(result.correctAnswerKey()),
                    result.explanation(),
                    result.sourceRefs()
            );
        }

        @Override
        public JsonNode sourceRefs() {
            return sourceRefs == null ? null : sourceRefs.deepCopy();
        }
    }

    @Schema(description = "선택지 키로 표현한 단일 답안")
    public record AnswerResponse(
            @Schema(description = "선택지 키", example = "A") String key
    ) {
    }
}
