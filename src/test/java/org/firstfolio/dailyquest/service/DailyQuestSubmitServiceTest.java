package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.domain.DailyQuestSubmitResult;
import org.firstfolio.dailyquest.mapper.DailyQuestMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointRewardResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestSubmitServiceTest {

    private static final long USER_ID = 10L;
    private static final long QUEST_ID = 4001L;
    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 13);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(
            2026,
            8,
            12,
            15,
            30
    );

    private DailyQuestMapper mapper;
    private DailyQuestRewardService rewardService;
    private DailyQuestSubmitService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DailyQuestMapper.class);
        rewardService = mock(DailyQuestRewardService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T15:30:00Z"),
                ZoneOffset.UTC
        );
        service = new DailyQuestSubmitService(
                mapper,
                rewardService,
                clock
        );
        when(mapper.findUserIdForUpdate(USER_ID)).thenReturn(USER_ID);
    }

    @Test
    void gradesFiveSnapshotsAndCompletesWithOneRewardTransaction() {
        DailyQuest quest = inProgressQuest();
        List<DailyQuestItem> items = answeredItems(
                List.of("A", "A", "B", "A", "A")
        );
        stubLockedQuest(quest, items);
        when(mapper.gradeItem(any())).thenReturn(1);
        when(rewardService.grant(
                USER_ID,
                QUEST_ID,
                4,
                COMPLETED_AT
        )).thenReturn(new PointRewardResult(91L, 400, 9001L));
        when(mapper.completeQuestIfInProgress(any())).thenReturn(1);

        DailyQuestSubmitResult result = service.submit(USER_ID);

        assertEquals(DailyQuestStatus.COMPLETED, result.status());
        assertEquals(4, result.correctCount());
        assertEquals(4, result.score());
        assertEquals(400, result.reward().points());
        assertEquals(9001L, result.reward().pointTransactionId());
        assertEquals(COMPLETED_AT, result.completedAt());
        assertEquals(5, result.results().size());
        assertFalse(result.results().get(2).correct());
        assertTrue(result.results().get(4).sourceRefs().isArray());
        assertEquals(
                "https://example.com/news",
                result.results().get(4).sourceRefs().get(0).path("url")
                        .textValue()
        );

        ArgumentCaptor<DailyQuestItem> itemCaptor =
                ArgumentCaptor.forClass(DailyQuestItem.class);
        verify(mapper, times(5)).gradeItem(itemCaptor.capture());
        assertEquals(
                List.of(true, true, false, true, true),
                itemCaptor.getAllValues().stream()
                        .map(DailyQuestItem::getCorrect)
                        .toList()
        );
        assertEquals(91L, quest.getRewardPolicyId());
        assertEquals(9001L, quest.getPointTransactionId());

        InOrder order = inOrder(mapper, rewardService);
        order.verify(mapper).findUserIdForUpdate(USER_ID);
        order.verify(mapper).findByUserIdAndQuestDateForUpdate(
                USER_ID,
                QUEST_DATE
        );
        order.verify(mapper).findItemsByDailyQuestIdForUpdate(QUEST_ID);
        order.verify(mapper, times(5)).gradeItem(any());
        order.verify(rewardService).grant(
                USER_ID,
                QUEST_ID,
                4,
                COMPLETED_AT
        );
        order.verify(mapper).completeQuestIfInProgress(quest);
    }

    @Test
    void completesZeroCorrectWithoutPointTransaction() {
        DailyQuest quest = inProgressQuest();
        List<DailyQuestItem> items = answeredItems(
                List.of("B", "B", "B", "B", "B")
        );
        stubLockedQuest(quest, items);
        when(mapper.gradeItem(any())).thenReturn(1);
        when(rewardService.grant(
                USER_ID,
                QUEST_ID,
                0,
                COMPLETED_AT
        )).thenReturn(new PointRewardResult(91L, 0, null));
        when(mapper.completeQuestIfInProgress(any())).thenReturn(1);

        DailyQuestSubmitResult result = service.submit(USER_ID);

        assertEquals(0, result.correctCount());
        assertEquals(0, result.reward().points());
        assertNull(result.reward().pointTransactionId());
        assertEquals(91L, quest.getRewardPolicyId());
        assertNull(quest.getPointTransactionId());
    }

    @Test
    void rejectsIncompleteQuestBeforeGradingOrReward() {
        DailyQuest quest = inProgressQuest();
        List<DailyQuestItem> items = new ArrayList<>(answeredItems(
                List.of("A", "A", "A", "A", "A")
        ));
        items.get(3).setUserAnswerJson(null);
        items.get(3).setAnsweredAt(null);
        stubLockedQuest(quest, items);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID)
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_INCOMPLETE,
                exception.getErrorCode()
        );
        verify(mapper, never()).gradeItem(any());
        verify(rewardService, never()).grant(
                anyLong(),
                anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                any()
        );
    }

    @Test
    void returnsNotFoundWhenTodayHasNoQuest() {
        when(mapper.findByUserIdAndQuestDateForUpdate(
                USER_ID,
                QUEST_DATE
        )).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID)
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(mapper, never()).findItemsByDailyQuestIdForUpdate(anyLong());
    }

    @Test
    void repeatedSubmitRestoresFirstResultWithoutGradingOrPayingAgain() {
        DailyQuest quest = inProgressQuest();
        quest.setStatus(DailyQuestStatus.COMPLETED);
        quest.setCorrectCount(4);
        quest.setScore(4);
        quest.setRewardPolicyId(91L);
        quest.setPointTransactionId(9001L);
        quest.setCompletedAt(COMPLETED_AT.minusMinutes(5));
        List<DailyQuestItem> items = answeredItems(
                List.of("A", "A", "B", "A", "A")
        );
        items.forEach(item -> item.setCorrect(
                !"B".equals(answerKey(item))
        ));
        stubLockedQuest(quest, items);
        when(rewardService.restore(
                USER_ID,
                QUEST_ID,
                91L,
                9001L
        )).thenReturn(new PointRewardResult(91L, 400, 9001L));

        DailyQuestSubmitResult result = service.submit(USER_ID);

        assertEquals(COMPLETED_AT.minusMinutes(5), result.completedAt());
        assertEquals(400, result.reward().points());
        verify(mapper, never()).gradeItem(any());
        verify(rewardService, never()).grant(
                anyLong(),
                anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                any()
        );
        verify(mapper, never()).completeQuestIfInProgress(any());
    }

    @Test
    void concurrentSubmitsSerializeOnQuestAndGrantOnlyOnce()
            throws Exception {
        DailyQuest quest = inProgressQuest();
        List<DailyQuestItem> items = answeredItems(
                List.of("A", "A", "B", "A", "A")
        );
        ReentrantLock questLock = new ReentrantLock();
        AtomicInteger grantCount = new AtomicInteger();
        AtomicInteger restoreCount = new AtomicInteger();

        when(mapper.findByUserIdAndQuestDateForUpdate(
                USER_ID,
                QUEST_DATE
        )).thenAnswer(invocation -> {
            questLock.lock();
            return quest;
        });
        when(mapper.findItemsByDailyQuestIdForUpdate(QUEST_ID))
                .thenReturn(items);
        when(mapper.gradeItem(any())).thenReturn(1);
        when(rewardService.grant(
                USER_ID,
                QUEST_ID,
                4,
                COMPLETED_AT
        )).thenAnswer(invocation -> {
            grantCount.incrementAndGet();
            return new PointRewardResult(91L, 400, 9001L);
        });
        when(mapper.completeQuestIfInProgress(any())).thenAnswer(
                invocation -> {
                    questLock.unlock();
                    return 1;
                }
        );
        when(rewardService.restore(
                USER_ID,
                QUEST_ID,
                91L,
                9001L
        )).thenAnswer(invocation -> {
            restoreCount.incrementAndGet();
            questLock.unlock();
            return new PointRewardResult(91L, 400, 9001L);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DailyQuestSubmitResult> first = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return service.submit(USER_ID);
            });
            Future<DailyQuestSubmitResult> second = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return service.submit(USER_ID);
            });
            start.countDown();

            DailyQuestSubmitResult firstResult = first.get(
                    5,
                    TimeUnit.SECONDS
            );
            DailyQuestSubmitResult secondResult = second.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertEquals(400, firstResult.reward().points());
            assertEquals(400, secondResult.reward().points());
            assertEquals(1, grantCount.get());
            assertEquals(1, restoreCount.get());
            verify(mapper, times(5)).gradeItem(any());
            verify(mapper, times(1)).completeQuestIfInProgress(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void detectsStoredResultThatDoesNotMatchSnapshot() {
        DailyQuest quest = inProgressQuest();
        quest.setStatus(DailyQuestStatus.COMPLETED);
        quest.setCorrectCount(5);
        quest.setScore(5);
        quest.setRewardPolicyId(91L);
        quest.setPointTransactionId(9001L);
        quest.setCompletedAt(COMPLETED_AT);
        List<DailyQuestItem> items = answeredItems(
                List.of("A", "A", "B", "A", "A")
        );
        items.forEach(item -> item.setCorrect(true));
        stubLockedQuest(quest, items);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(rewardService, never()).restore(
                anyLong(),
                anyLong(),
                any(),
                any()
        );
    }

    private void stubLockedQuest(
            DailyQuest quest,
            List<DailyQuestItem> items
    ) {
        when(mapper.findByUserIdAndQuestDateForUpdate(
                USER_ID,
                QUEST_DATE
        )).thenReturn(quest);
        when(mapper.findItemsByDailyQuestIdForUpdate(QUEST_ID))
                .thenReturn(items);
    }

    private DailyQuest inProgressQuest() {
        DailyQuest quest = DailyQuest.assigned(USER_ID, QUEST_DATE);
        quest.setDailyQuestId(QUEST_ID);
        quest.setStatus(DailyQuestStatus.IN_PROGRESS);
        return quest;
    }

    private List<DailyQuestItem> answeredItems(List<String> keys) {
        List<DailyQuestItem> items = new ArrayList<>();
        for (int index = 0; index < DailyQuest.TOTAL_QUESTION_COUNT; index++) {
            DailyQuestItem item = DailyQuestItem.assigned(
                    QUEST_ID,
                    1001L + index,
                    index + 1,
                    snapshot(index == 4),
                    COMPLETED_AT.minusHours(1)
            );
            item.setDailyQuestItemId(5001L + index);
            item.setUserAnswerJson(
                    "{\"key\":\"" + keys.get(index) + "\"}"
            );
            item.setAnsweredAt(COMPLETED_AT.minusMinutes(10 - index));
            items.add(item);
        }
        return items;
    }

    private String snapshot(boolean ai) {
        String sourceRefs = ai
                ? "[{\"title\":\"뉴스\","
                    + "\"url\":\"https://example.com/news\","
                    + "\"reference_at\":\"2026-08-13T00:00:00Z\"}]"
                : "null";
        return "{"
                + "\"generation_type\":\"" + (ai ? "AI" : "HUMAN") + "\","
                + "\"options_json\":["
                + "{\"key\":\"A\",\"label\":\"정답\"},"
                + "{\"key\":\"B\",\"label\":\"오답\"}],"
                + "\"correct_answer_json\":{\"key\":\"A\"},"
                + "\"explanation\":\"정답 해설\","
                + "\"source_refs_json\":" + sourceRefs
                + "}";
    }

    private String answerKey(DailyQuestItem item) {
        return item.getUserAnswerJson().contains("\"B\"") ? "B" : "A";
    }
}
