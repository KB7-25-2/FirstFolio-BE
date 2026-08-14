package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.springframework.stereotype.Component;

/**
 * {@link QuizQuestionPayloadValidator}가 먼저 통과해서 main_chapter_id가 null이 아니고,
 * sub_chapter_id 유무가 usage_type과 이미 맞는 상태라고 전제하고 호출한다.
 */
@Component
public class QuizChapterScopeValidator {

    private final MainChapterMapper mainChapterMapper;
    private final SubChapterMapper subChapterMapper;

    public QuizChapterScopeValidator(
            MainChapterMapper mainChapterMapper,
            SubChapterMapper subChapterMapper
    ) {
        this.mainChapterMapper = mainChapterMapper;
        this.subChapterMapper = subChapterMapper;
    }

    public QuizItemValidationResult validate(QuizQuestionRequest quiz) {
        MainChapter mainChapter = mainChapterMapper.findById(quiz.mainChapterId());
        if (mainChapter == null) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.CHAPTER_NOT_FOUND,
                    "요청한 대단원을 찾을 수 없습니다: " + quiz.mainChapterId()
            );
        }
        if (!mainChapter.isActive()) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.CHAPTER_NOT_ACTIVE,
                    "요청한 대단원이 현재 서비스 대상이 아닙니다: " + quiz.mainChapterId()
            );
        }

        if (quiz.subChapterId() == null) {
            return QuizItemValidationResult.valid();
        }

        SubChapter subChapter = subChapterMapper.findById(quiz.subChapterId());
        if (subChapter == null) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.CHAPTER_NOT_FOUND,
                    "요청한 소단원을 찾을 수 없습니다: " + quiz.subChapterId()
            );
        }
        if (!subChapter.isActive()) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.CHAPTER_NOT_ACTIVE,
                    "요청한 소단원이 현재 서비스 대상이 아닙니다: " + quiz.subChapterId()
            );
        }
        if (subChapter.getMainChapterId() != quiz.mainChapterId()) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_CHAPTER_SCOPE,
                    "소단원이 요청한 대단원에 속하지 않습니다: sub_chapter_id="
                            + quiz.subChapterId() + ", main_chapter_id=" + quiz.mainChapterId()
            );
        }

        return QuizItemValidationResult.valid();
    }
}
