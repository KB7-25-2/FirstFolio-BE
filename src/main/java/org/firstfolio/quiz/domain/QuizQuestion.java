package org.firstfolio.quiz.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class QuizQuestion {

    private Long questionId;
    private String questionKey;
    private int versionNo;
    private QuizUsageType usageType;
    private Long mainChapterId;
    private Long subChapterId;
    private Integer displayOrder;
    private QuizQuestionType questionType;
    private QuizDifficulty difficulty;
    private String prompt;
    private String scenarioJson;
    private String optionsJson;
    private String correctAnswerJson;
    private String explanation;
    private QuizGenerationType generationType;
    private LocalDate questDate;
    private String sourceRefsJson;
    private QuizQuestionStatus status;
    private long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public QuizQuestion() {
    }

    public static QuizQuestion draft(
            String questionKey,
            int versionNo,
            QuizUsageType usageType,
            Long mainChapterId,
            Long subChapterId,
            Integer displayOrder,
            QuizQuestionType questionType,
            QuizDifficulty difficulty,
            String prompt,
            String scenarioJson,
            String optionsJson,
            String correctAnswerJson,
            String explanation,
            QuizGenerationType generationType,
            LocalDate questDate,
            String sourceRefsJson,
            long createdBy,
            LocalDateTime createdAt
    ) {
        QuizQuestion question = new QuizQuestion();
        question.questionKey = questionKey;
        question.versionNo = versionNo;
        question.usageType = usageType;
        question.mainChapterId = mainChapterId;
        question.subChapterId = subChapterId;
        question.displayOrder = displayOrder;
        question.questionType = questionType;
        question.difficulty = difficulty;
        question.prompt = prompt;
        question.scenarioJson = scenarioJson;
        question.optionsJson = optionsJson;
        question.correctAnswerJson = correctAnswerJson;
        question.explanation = explanation;
        question.generationType = generationType;
        question.questDate = questDate;
        question.sourceRefsJson = sourceRefsJson;
        question.status = QuizQuestionStatus.DRAFT;
        question.createdBy = createdBy;
        question.createdAt = createdAt;
        return question;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getQuestionKey() {
        return questionKey;
    }

    public void setQuestionKey(String questionKey) {
        this.questionKey = questionKey;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public QuizUsageType getUsageType() {
        return usageType;
    }

    public void setUsageType(QuizUsageType usageType) {
        this.usageType = usageType;
    }

    public Long getMainChapterId() {
        return mainChapterId;
    }

    public void setMainChapterId(Long mainChapterId) {
        this.mainChapterId = mainChapterId;
    }

    public Long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(Long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public QuizQuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuizQuestionType questionType) {
        this.questionType = questionType;
    }

    public QuizDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(QuizDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getScenarioJson() {
        return scenarioJson;
    }

    public void setScenarioJson(String scenarioJson) {
        this.scenarioJson = scenarioJson;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public String getCorrectAnswerJson() {
        return correctAnswerJson;
    }

    public void setCorrectAnswerJson(String correctAnswerJson) {
        this.correctAnswerJson = correctAnswerJson;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public QuizGenerationType getGenerationType() {
        return generationType;
    }

    public void setGenerationType(QuizGenerationType generationType) {
        this.generationType = generationType;
    }

    public LocalDate getQuestDate() {
        return questDate;
    }

    public void setQuestDate(LocalDate questDate) {
        this.questDate = questDate;
    }

    public String getSourceRefsJson() {
        return sourceRefsJson;
    }

    public void setSourceRefsJson(String sourceRefsJson) {
        this.sourceRefsJson = sourceRefsJson;
    }

    public QuizQuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuizQuestionStatus status) {
        this.status = status;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
