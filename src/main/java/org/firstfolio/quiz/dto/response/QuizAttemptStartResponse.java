package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStartResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizType;

import java.util.List;

@Schema(description = "소단원 퀴즈 응시 시작 결과")
public record QuizAttemptStartResponse(
        @Schema(description = "응시 ID", example = "3001") long attemptId,
        @Schema(description = "퀴즈 유형", example = "SUB_CHAPTER") QuizType quizType,
        @Schema(description = "소단원 ID", example = "101") long subChapterId,
        @Schema(description = "학습한 강좌 콘텐츠 버전 ID", example = "301") long contentVersionId,
        @Schema(description = "응시 상태", example = "IN_PROGRESS") QuizAttemptStatus status,
        @Schema(description = "문항 수", example = "3") int questionCount,
        @Schema(description = "정답·해설을 제외한 출제 문항") List<QuizAttemptQuestionResponse> questions
) {
    public QuizAttemptStartResponse {
        questions = List.copyOf(questions);
    }

    public static QuizAttemptStartResponse from(QuizAttemptStartResult result) {
        QuizAttempt attempt = result.attempt();
        return new QuizAttemptStartResponse(
                attempt.getAttemptId(),
                attempt.getQuizType(),
                attempt.getSubChapterId(),
                attempt.getContentVersionId(),
                attempt.getStatus(),
                result.questions().size(),
                result.questions().stream()
                        .map(QuizAttemptQuestionResponse::from)
                        .toList()
        );
    }
}
