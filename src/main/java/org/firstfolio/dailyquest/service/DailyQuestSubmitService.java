package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestItemGradingResult;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.domain.DailyQuestSubmitResult;
import org.firstfolio.dailyquest.mapper.DailyQuestMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointRewardResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DailyQuestSubmitService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyQuestMapper dailyQuestMapper;
    private final DailyQuestRewardService rewardService;
    private final Clock clock;
    private final DailyQuestSubmissionSnapshotCodec snapshotCodec;

    public DailyQuestSubmitService(
            DailyQuestMapper dailyQuestMapper,
            DailyQuestRewardService rewardService,
            Clock clock
    ) {
        this.dailyQuestMapper = dailyQuestMapper;
        this.rewardService = rewardService;
        this.clock = clock;
        this.snapshotCodec = new DailyQuestSubmissionSnapshotCodec();
    }

    @Transactional
    public DailyQuestSubmitResult submit(long userId) {
        Instant submittedInstant = clock.instant();
        LocalDate questDate = LocalDate.ofInstant(
                submittedInstant,
                SERVICE_ZONE
        );
        LocalDateTime completedAt = LocalDateTime.ofInstant(
                submittedInstant,
                ZoneOffset.UTC
        );

        if (userId <= 0
                || dailyQuestMapper.findUserIdForUpdate(userId) == null) {
            throw new ApiException(ErrorCode.DAILY_QUEST_NOT_FOUND);
        }
        DailyQuest dailyQuest = dailyQuestMapper
                .findByUserIdAndQuestDateForUpdate(userId, questDate);
        requireOwnedQuest(dailyQuest, userId, questDate);

        List<DailyQuestItem> items = dailyQuestMapper
                .findItemsByDailyQuestIdForUpdate(
                        dailyQuest.getDailyQuestId()
                );
        validateItemSet(dailyQuest, items);

        if (dailyQuest.getStatus() == DailyQuestStatus.COMPLETED) {
            return restore(dailyQuest, items);
        }
        if (items.stream().anyMatch(
                item -> item.getUserAnswerJson() == null
        )) {
            throw new ApiException(ErrorCode.DAILY_QUEST_INCOMPLETE);
        }
        requireReadyToGrade(dailyQuest, items);

        List<DailyQuestItemGradingResult> results = items.stream()
                .map(snapshotCodec::grade)
                .toList();
        for (int index = 0; index < items.size(); index++) {
            DailyQuestItem item = items.get(index);
            item.setCorrect(results.get(index).correct());
            if (dailyQuestMapper.gradeItem(item) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }

        int correctCount = (int) results.stream()
                .filter(DailyQuestItemGradingResult::correct)
                .count();
        PointRewardResult reward = rewardService.grant(
                userId,
                dailyQuest.getDailyQuestId(),
                correctCount,
                completedAt
        );

        dailyQuest.setStatus(DailyQuestStatus.COMPLETED);
        dailyQuest.setCorrectCount(correctCount);
        dailyQuest.setScore(correctCount);
        dailyQuest.setRewardPolicyId(reward.policyId());
        dailyQuest.setPointTransactionId(reward.pointTransactionId());
        dailyQuest.setCompletedAt(completedAt);
        if (dailyQuestMapper.completeQuestIfInProgress(dailyQuest) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        return result(dailyQuest, reward, results);
    }

    private DailyQuestSubmitResult restore(
            DailyQuest dailyQuest,
            List<DailyQuestItem> items
    ) {
        if (dailyQuest.getCompletedAt() == null
                || dailyQuest.getRewardPolicyId() == null
                || items.stream().anyMatch(
                    item -> item.getUserAnswerJson() == null
                            || item.getCorrect() == null
                            || item.getAnsweredAt() == null
                )) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        List<DailyQuestItemGradingResult> results = items.stream()
                .map(snapshotCodec::grade)
                .toList();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getCorrect()
                    != results.get(index).correct()) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }
        int correctCount = (int) results.stream()
                .filter(DailyQuestItemGradingResult::correct)
                .count();
        if (dailyQuest.getCorrectCount() != correctCount
                || dailyQuest.getScore() != correctCount) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        PointRewardResult reward = rewardService.restore(
                dailyQuest.getUserId(),
                dailyQuest.getDailyQuestId(),
                dailyQuest.getRewardPolicyId(),
                dailyQuest.getPointTransactionId()
        );
        return result(dailyQuest, reward, results);
    }

    private DailyQuestSubmitResult result(
            DailyQuest dailyQuest,
            PointRewardResult reward,
            List<DailyQuestItemGradingResult> results
    ) {
        return new DailyQuestSubmitResult(
                dailyQuest.getDailyQuestId(),
                dailyQuest.getStatus(),
                dailyQuest.getCorrectCount(),
                dailyQuest.getScore(),
                reward,
                results,
                dailyQuest.getCompletedAt()
        );
    }

    private void requireOwnedQuest(
            DailyQuest dailyQuest,
            long userId,
            LocalDate questDate
    ) {
        if (dailyQuest == null) {
            throw new ApiException(ErrorCode.DAILY_QUEST_NOT_FOUND);
        }
        if (dailyQuest.getDailyQuestId() == null
                || dailyQuest.getDailyQuestId() <= 0
                || dailyQuest.getUserId() != userId
                || !questDate.equals(dailyQuest.getQuestDate())
                || dailyQuest.getStatus() == null
                || dailyQuest.getTotalCount()
                    != DailyQuest.TOTAL_QUESTION_COUNT) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateItemSet(
            DailyQuest dailyQuest,
            List<DailyQuestItem> items
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
                    || item.getDailyQuestId()
                        != dailyQuest.getDailyQuestId()
                    || item.getQuestionId() <= 0
                    || !questionIds.add(item.getQuestionId())
                    || item.getDisplayOrder() != index + 1
                    || item.getQuestionSnapshotJson() == null
                    || item.getQuestionSnapshotJson().isBlank()) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }
    }

    private void requireReadyToGrade(
            DailyQuest dailyQuest,
            List<DailyQuestItem> items
    ) {
        if (dailyQuest.getStatus() != DailyQuestStatus.IN_PROGRESS
                || dailyQuest.getCorrectCount() != 0
                || dailyQuest.getScore() != 0
                || dailyQuest.getRewardPolicyId() != null
                || dailyQuest.getPointTransactionId() != null
                || dailyQuest.getCompletedAt() != null
                || items.stream().anyMatch(
                    item -> item.getCorrect() != null
                            || item.getAnsweredAt() == null
                )) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
