package org.firstfolio.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveCommand;

import java.util.List;

@Schema(description = "레벨 테스트 답안 일괄 저장 요청")
public record LevelTestAnswerSaveRequest(
        @Schema(description = "저장할 한 개 이상의 문항 답안") List<LevelTestAnswerItemRequest> answers
) {
    public List<LevelTestAnswerSaveCommand> toCommands() {
        if (answers == null) {
            return null;
        }
        return answers.stream()
                .map(answer -> answer == null
                        ? null
                        : new LevelTestAnswerSaveCommand(
                                answer.questionId(),
                                answer.selectedKey()
                        ))
                .toList();
    }
}
