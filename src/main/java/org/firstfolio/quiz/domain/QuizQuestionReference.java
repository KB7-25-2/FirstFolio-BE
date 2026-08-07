package org.firstfolio.quiz.domain;

public class QuizQuestionReference {

    private Long questionId;
    private QuizUsageType usageType;
    private Long subChapterId;
    private QuizQuestionStatus status;

    public QuizQuestionReference() {
    }

    public QuizQuestionReference(
            Long questionId,
            QuizUsageType usageType,
            Long subChapterId,
            QuizQuestionStatus status
    ) {
        this.questionId = questionId;
        this.usageType = usageType;
        this.subChapterId = subChapterId;
        this.status = status;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public QuizUsageType getUsageType() {
        return usageType;
    }

    public void setUsageType(QuizUsageType usageType) {
        this.usageType = usageType;
    }

    public Long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(Long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public QuizQuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuizQuestionStatus status) {
        this.status = status;
    }
}
