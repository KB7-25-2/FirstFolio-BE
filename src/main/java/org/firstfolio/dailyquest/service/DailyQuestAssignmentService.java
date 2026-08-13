package org.firstfolio.dailyquest.service;

import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestAssignmentResult;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestWrongAnswer;
import org.firstfolio.dailyquest.mapper.DailyQuestMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.firstfolio.quiz.service.QuizQuestionSnapshotCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class DailyQuestAssignmentService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int RECENT_EXCLUSION_DAYS = 7;

    private final DailyQuestMapper dailyQuestMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final UserCurriculumMapper userCurriculumMapper;
    private final Clock clock;
    private final DailyQuestQuestionSelector selector;
    private final QuizQuestionSnapshotCodec snapshotCodec;

    public DailyQuestAssignmentService(
            DailyQuestMapper dailyQuestMapper,
            QuizQuestionMapper quizQuestionMapper,
            UserCurriculumMapper userCurriculumMapper,
            Clock clock
    ) {
        this.dailyQuestMapper = dailyQuestMapper;
        this.quizQuestionMapper = quizQuestionMapper;
        this.userCurriculumMapper = userCurriculumMapper;
        this.clock = clock;
        this.selector = new DailyQuestQuestionSelector();
        this.snapshotCodec = new QuizQuestionSnapshotCodec();
    }

    @Transactional
    public DailyQuestAssignmentResult assignToday(long userId) {
        LocalDate questDate = LocalDate.ofInstant(
                clock.instant(),
                SERVICE_ZONE
        );
        return assign(userId, questDate);
    }

    DailyQuestAssignmentResult assign(long userId, LocalDate questDate) {
        if (userId <= 0 || questDate == null) {
            throw new ApiException(ErrorCode.DAILY_QUEST_NOT_AVAILABLE);
        }
        if (dailyQuestMapper.findUserIdForUpdate(userId) == null) {
            throw new ApiException(ErrorCode.DAILY_QUEST_NOT_AVAILABLE);
        }

        DailyQuest existing = dailyQuestMapper
                .findByUserIdAndQuestDateForUpdate(userId, questDate);
        if (existing != null) {
            return restore(existing, userId, questDate);
        }
        if (userCurriculumMapper.findActiveByUserId(userId).isEmpty()) {
            throw new ApiException(ErrorCode.DAILY_QUEST_NOT_AVAILABLE);
        }

        List<DailyQuestWrongAnswer> wrongAnswers = dailyQuestMapper
                .findUnresolvedWrongAnswers(userId);
        Set<String> recentQuestionKeys = new HashSet<>(
                dailyQuestMapper.findRecentlyAssignedGeneralQuestionKeys(
                        userId,
                        questDate.minusDays(RECENT_EXCLUSION_DAYS),
                        questDate
                )
        );
        List<QuizQuestion> generalQuestions = selector.select(
                quizQuestionMapper
                        .findLatestPublishedDailyGeneralQuestions(),
                wrongAnswers,
                recentQuestionKeys
        );
        QuizQuestion newsQuestion = quizQuestionMapper
                .findLatestPublishedDailyNewsByQuestDate(questDate);
        validateNewsQuestion(newsQuestion, questDate, generalQuestions);

        return create(
                userId,
                questDate,
                generalQuestions,
                newsQuestion
        );
    }

    private DailyQuestAssignmentResult create(
            long userId,
            LocalDate questDate,
            List<QuizQuestion> generalQuestions,
            QuizQuestion newsQuestion
    ) {
        DailyQuest dailyQuest = DailyQuest.assigned(userId, questDate);
        if (dailyQuestMapper.insertQuest(dailyQuest) != 1
                || dailyQuest.getDailyQuestId() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        LocalDateTime createdAt = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );
        List<QuizQuestion> questions = new ArrayList<>(generalQuestions);
        questions.add(newsQuestion);

        List<DailyQuestItem> items = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            QuizQuestion question = questions.get(index);
            DailyQuestItem item = DailyQuestItem.assigned(
                    dailyQuest.getDailyQuestId(),
                    question.getQuestionId(),
                    index + 1,
                    snapshotCodec.createVersionedSnapshot(question),
                    createdAt
            );
            if (dailyQuestMapper.insertItem(item) != 1
                    || item.getDailyQuestItemId() == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
            items.add(item);
        }
        return new DailyQuestAssignmentResult(dailyQuest, items);
    }

    private DailyQuestAssignmentResult restore(
            DailyQuest dailyQuest,
            long userId,
            LocalDate questDate
    ) {
        if (dailyQuest.getDailyQuestId() == null
                || dailyQuest.getDailyQuestId() <= 0
                || dailyQuest.getUserId() != userId
                || !Objects.equals(dailyQuest.getQuestDate(), questDate)
                || dailyQuest.getTotalCount()
                    != DailyQuest.TOTAL_QUESTION_COUNT) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        List<DailyQuestItem> items = dailyQuestMapper
                .findItemsByDailyQuestId(dailyQuest.getDailyQuestId());
        validateStoredItems(items, dailyQuest.getDailyQuestId());
        return new DailyQuestAssignmentResult(dailyQuest, items);
    }

    private void validateStoredItems(
            List<DailyQuestItem> items,
            long dailyQuestId
    ) {
        if (items == null
                || items.size() != DailyQuest.TOTAL_QUESTION_COUNT) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        Set<Long> questionIds = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            DailyQuestItem item = items.get(index);
            if (item == null
                    || item.getDailyQuestItemId() == null
                    || item.getDailyQuestItemId() <= 0
                    || item.getDailyQuestId() != dailyQuestId
                    || item.getQuestionId() <= 0
                    || !questionIds.add(item.getQuestionId())
                    || item.getDisplayOrder() != index + 1
                    || item.getQuestionSnapshotJson() == null
                    || item.getQuestionSnapshotJson().isBlank()) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }
    }

    private void validateNewsQuestion(
            QuizQuestion newsQuestion,
            LocalDate questDate,
            List<QuizQuestion> generalQuestions
    ) {
        if (newsQuestion == null
                || newsQuestion.getQuestionId() == null
                || newsQuestion.getQuestionId() <= 0
                || newsQuestion.getQuestionKey() == null
                || newsQuestion.getQuestionKey().isBlank()
                || newsQuestion.getVersionNo() <= 0
                || newsQuestion.getUsageType() != QuizUsageType.DAILY_NEWS
                || newsQuestion.getStatus() != QuizQuestionStatus.PUBLISHED
                || newsQuestion.getQuestionType()
                    != QuizQuestionType.SCENARIO
                || newsQuestion.getGenerationType()
                    != QuizGenerationType.AI
                || !Objects.equals(newsQuestion.getQuestDate(), questDate)
                || generalQuestions.stream().anyMatch(
                    question -> Objects.equals(
                            question.getQuestionKey(),
                            newsQuestion.getQuestionKey()
                    )
                )) {
            throw new ApiException(ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE);
        }
    }
}
