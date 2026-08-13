package org.firstfolio.dailyquest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.dailyquest.domain.DailyQuestQuestionView;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.domain.DailyQuestTodayResult;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.dto.response.QuizChoiceResponse;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "오늘의 일일 퀘스트 배정 및 진행 상태")
public record DailyQuestTodayResponse(
        @Schema(description = "일일 퀘스트 ID", example = "4001") long dailyQuestId,
        @Schema(description = "서비스 기준 퀘스트 날짜", example = "2026-08-13") LocalDate questDate,
        @Schema(description = "진행 상태", example = "IN_PROGRESS") DailyQuestStatus status,
        @Schema(description = "답안을 저장한 문항 수", example = "2") int answeredCount,
        @Schema(description = "전체 문항 수", example = "5") int totalCount,
        @Schema(description = "정답·해설·내부 근거를 제외한 배정 문항") List<QuestionResponse> questions
) {
    public DailyQuestTodayResponse {
        questions = List.copyOf(questions);
    }

    public static DailyQuestTodayResponse from(DailyQuestTodayResult result) {
        return new DailyQuestTodayResponse(
                result.dailyQuest().getDailyQuestId(),
                result.dailyQuest().getQuestDate(),
                result.dailyQuest().getStatus(),
                result.answeredCount(),
                result.dailyQuest().getTotalCount(),
                result.questions().stream()
                        .map(QuestionResponse::from)
                        .toList()
        );
    }

    @Schema(description = "일일 퀘스트에 고정된 사용자용 문항")
    public record QuestionResponse(
            @Schema(description = "일일 퀘스트 문항 배정 ID", example = "5001") long dailyQuestItemId,
            @Schema(description = "문항 버전 ID", example = "1001") long questionId,
            @Schema(description = "표시 순서", example = "1") int displayOrder,
            @Schema(description = "문항 유형", example = "SINGLE_CHOICE") QuizQuestionType questionType,
            @Schema(description = "문항 생성 방식", example = "HUMAN") QuizGenerationType generationType,
            @Schema(description = "문제 본문") String prompt,
            @Schema(description = "시나리오. 일반 문항은 null", nullable = true) JsonNode scenario,
            @Schema(description = "선택지 목록") List<QuizChoiceResponse> choices,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            @Schema(description = "저장된 답안. 미응답이면 null", nullable = true) SavedAnswerResponse savedAnswer
    ) {
        public QuestionResponse {
            scenario = scenario == null ? null : scenario.deepCopy();
            choices = List.copyOf(choices);
        }

        private static QuestionResponse from(DailyQuestQuestionView question) {
            return new QuestionResponse(
                    question.dailyQuestItemId(),
                    question.questionId(),
                    question.displayOrder(),
                    question.questionType(),
                    question.generationType(),
                    question.prompt(),
                    question.scenario(),
                    question.choices().stream()
                            .map(QuizChoiceResponse::from)
                            .toList(),
                    question.savedAnswerKey() == null
                            ? null
                            : new SavedAnswerResponse(
                                    question.savedAnswerKey()
                            )
            );
        }

        @Override
        public JsonNode scenario() {
            return scenario == null ? null : scenario.deepCopy();
        }
    }

    @Schema(description = "최종 제출 전 저장된 단일 답안")
    public record SavedAnswerResponse(
            @Schema(description = "선택지 키", example = "A") String key
    ) {
    }
}
