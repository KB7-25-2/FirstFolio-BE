package org.firstfolio.quiz.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.quiz.domain.LevelTestAttemptQuestion;
import org.firstfolio.quiz.domain.LevelTestAttemptStartResult;
import org.firstfolio.quiz.domain.LevelTestSavedAnswer;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;

import java.util.List;

@Schema(description = "레벨 테스트 응시 시작 또는 복원 결과")
public record LevelTestAttemptStartResponse(
        @Schema(description = "응시 ID", example = "2001") long attemptId,
        @Schema(description = "응시 상태", example = "IN_PROGRESS") QuizAttemptStatus status,
        @Schema(description = "전체 문항 수", example = "8") int questionCount,
        @Schema(description = "정답·해설을 제외한 배정 문항") List<QuestionResponse> questions,
        @Schema(description = "최종 제출 전 저장된 답안") List<SavedAnswerResponse> answers
) {
    public LevelTestAttemptStartResponse {
        questions = List.copyOf(questions);
        answers = List.copyOf(answers);
    }

    public static LevelTestAttemptStartResponse from(
            LevelTestAttemptStartResult result
    ) {
        return new LevelTestAttemptStartResponse(
                result.attempt().getAttemptId(),
                result.attempt().getStatus(),
                result.questions().size(),
                result.questions().stream().map(QuestionResponse::from).toList(),
                result.answers().stream().map(SavedAnswerResponse::from).toList()
        );
    }

    @Schema(description = "레벨 테스트에 배정된 문항")
    public record QuestionResponse(
            @Schema(description = "문항 버전 ID", example = "1001") long questionId,
            @Schema(description = "응시 내 표시 순서", example = "1") int displayOrder,
            @Schema(description = "소속 대단원") MainChapterResponse mainChapter,
            @Schema(description = "문항 유형", example = "SINGLE_CHOICE") QuizQuestionType questionType,
            @Schema(description = "문항 생성 방식", example = "HUMAN") QuizGenerationType generationType,
            @Schema(description = "문제 본문") String prompt,
            @Schema(description = "시나리오. 일반 문항은 null", nullable = true) JsonNode scenario,
            @Schema(description = "선택지 목록") List<QuizChoiceResponse> choices
    ) {
        public QuestionResponse {
            scenario = scenario == null ? null : scenario.deepCopy();
            choices = List.copyOf(choices);
        }

        private static QuestionResponse from(LevelTestAttemptQuestion question) {
            return new QuestionResponse(
                    question.questionId(),
                    question.displayOrder(),
                    new MainChapterResponse(
                            question.mainChapterId(),
                            question.assetType()
                    ),
                    question.questionType(),
                    question.generationType(),
                    question.prompt(),
                    question.scenario(),
                    question.choices().stream()
                            .map(QuizChoiceResponse::from)
                            .toList()
            );
        }

        @Override
        public JsonNode scenario() {
            return scenario == null ? null : scenario.deepCopy();
        }
    }

    @Schema(description = "문항 소속 대단원")
    public record MainChapterResponse(
            @Schema(description = "대단원 ID", example = "2") long mainChapterId,
            @Schema(description = "자산 유형", example = "DEPOSIT_SAVINGS") AssetType assetType
    ) {
    }

    @Schema(description = "저장된 레벨 테스트 답안")
    public record SavedAnswerResponse(
            @Schema(description = "문항 ID", example = "1001") long questionId,
            @Schema(description = "저장된 단일 답안") AnswerSelectionResponse answer
    ) {
        private static SavedAnswerResponse from(LevelTestSavedAnswer answer) {
            return new SavedAnswerResponse(
                    answer.questionId(),
                    new AnswerSelectionResponse(answer.key())
            );
        }
    }

    @Schema(description = "저장된 단일 답안")
    public record AnswerSelectionResponse(
            @Schema(description = "선택지 키", example = "B") String key
    ) {
    }
}
