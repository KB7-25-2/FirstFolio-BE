package org.firstfolio.dailyquest.service;

import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestAssignmentResult;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.mapper.DailyQuestMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestAssignmentServiceTest {

    private static final long USER_ID = 10L;
    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 13);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(
            2026,
            8,
            12,
            15,
            30
    );

    private DailyQuestMapper dailyQuestMapper;
    private QuizQuestionMapper quizQuestionMapper;
    private UserCurriculumMapper userCurriculumMapper;
    private DailyQuestAssignmentService service;

    @BeforeEach
    void setUp() {
        dailyQuestMapper = mock(DailyQuestMapper.class);
        quizQuestionMapper = mock(QuizQuestionMapper.class);
        userCurriculumMapper = mock(UserCurriculumMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T15:30:00Z"),
                ZoneOffset.UTC
        );
        service = new DailyQuestAssignmentService(
                dailyQuestMapper,
                quizQuestionMapper,
                userCurriculumMapper,
                clock
        );

        when(dailyQuestMapper.findUserIdForUpdate(USER_ID))
                .thenReturn(USER_ID);
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of(curriculumItem()));
        when(dailyQuestMapper.findUnresolvedWrongAnswers(USER_ID))
                .thenReturn(List.of());
        when(dailyQuestMapper.findRecentlyAssignedGeneralQuestionKeys(
                USER_ID,
                QUEST_DATE.minusDays(7),
                QUEST_DATE
        )).thenReturn(List.of());
    }

    @Test
    void assignsFourGeneralQuestionsAndTodaysNewsInOneSnapshotSet() {
        List<QuizQuestion> generalQuestions = List.of(
                general(101L, "general-101", 10L, 1001L),
                general(102L, "general-102", 10L, null),
                general(103L, "general-103", 20L, null),
                general(104L, "general-104", 30L, null)
        );
        when(quizQuestionMapper
                .findLatestPublishedDailyGeneralQuestions())
                .thenReturn(generalQuestions);
        when(quizQuestionMapper
                .findLatestPublishedDailyNewsByQuestDate(QUEST_DATE))
                .thenReturn(news(105L, "news-105"));
        when(dailyQuestMapper.insertQuest(any())).thenAnswer(invocation -> {
            DailyQuest dailyQuest = invocation.getArgument(0);
            dailyQuest.setDailyQuestId(4001L);
            return 1;
        });
        AtomicLong itemId = new AtomicLong(5000L);
        when(dailyQuestMapper.insertItem(any())).thenAnswer(invocation -> {
            DailyQuestItem item = invocation.getArgument(0);
            item.setDailyQuestItemId(itemId.incrementAndGet());
            return 1;
        });

        DailyQuestAssignmentResult result = service.assignToday(USER_ID);

        assertEquals(4001L, result.dailyQuest().getDailyQuestId());
        assertEquals(QUEST_DATE, result.dailyQuest().getQuestDate());
        assertEquals(5, result.items().size());
        assertEquals(
                List.of(101L, 102L, 103L, 104L, 105L),
                result.items().stream()
                        .map(DailyQuestItem::getQuestionId)
                        .toList()
        );
        assertEquals(
                List.of(1, 2, 3, 4, 5),
                result.items().stream()
                        .map(DailyQuestItem::getDisplayOrder)
                        .toList()
        );
        DailyQuestItem newsItem = result.items().get(4);
        assertEquals(CREATED_AT, newsItem.getCreatedAt());
        assertNull(newsItem.getUserAnswerJson());
        assertNull(newsItem.getCorrect());
        assertTrue(newsItem.getQuestionSnapshotJson().contains(
                "\"prompt\":\"오늘의 뉴스 문제\""
        ));
        assertTrue(newsItem.getQuestionSnapshotJson().contains(
                "\"correct_answer_json\""
        ));
        assertTrue(newsItem.getQuestionSnapshotJson().contains(
                "\"question_key\":\"news-105\""
        ));
        assertTrue(newsItem.getQuestionSnapshotJson().contains(
                "\"version_no\":1"
        ));

        InOrder order = inOrder(dailyQuestMapper);
        order.verify(dailyQuestMapper).findUserIdForUpdate(USER_ID);
        order.verify(dailyQuestMapper)
                .findByUserIdAndQuestDateForUpdate(USER_ID, QUEST_DATE);
        order.verify(dailyQuestMapper).insertQuest(any());
        order.verify(dailyQuestMapper, times(5)).insertItem(any());
    }

    @Test
    void restoresTheSameQuestWithoutSelectingOrInsertingAgain() {
        DailyQuest existing = DailyQuest.assigned(USER_ID, QUEST_DATE);
        existing.setDailyQuestId(4001L);
        List<DailyQuestItem> items = storedItems(4001L);
        when(dailyQuestMapper.findByUserIdAndQuestDateForUpdate(
                USER_ID,
                QUEST_DATE
        )).thenReturn(existing);
        when(dailyQuestMapper.findItemsByDailyQuestId(4001L))
                .thenReturn(items);

        DailyQuestAssignmentResult result = service.assignToday(USER_ID);

        assertEquals(4001L, result.dailyQuest().getDailyQuestId());
        assertEquals(
                items.stream().map(DailyQuestItem::getQuestionId).toList(),
                result.items().stream()
                        .map(DailyQuestItem::getQuestionId)
                        .toList()
        );
        verify(userCurriculumMapper, never()).findActiveByUserId(USER_ID);
        verify(quizQuestionMapper, never())
                .findLatestPublishedDailyGeneralQuestions();
        verify(dailyQuestMapper, never()).insertQuest(any());
        verify(dailyQuestMapper, never()).insertItem(any());
    }

    @Test
    void concurrentRequestsCreateOneQuestAndRestoreTheSameAssignment()
            throws Exception {
        List<QuizQuestion> generalQuestions = List.of(
                general(101L, "general-101", 10L, null),
                general(102L, "general-102", 20L, null),
                general(103L, "general-103", 30L, null),
                general(104L, "general-104", 40L, null)
        );
        when(quizQuestionMapper
                .findLatestPublishedDailyGeneralQuestions())
                .thenReturn(generalQuestions);
        when(quizQuestionMapper
                .findLatestPublishedDailyNewsByQuestDate(QUEST_DATE))
                .thenReturn(news(105L, "news-105"));

        ReentrantLock userRowLock = new ReentrantLock(true);
        AtomicReference<DailyQuest> storedQuest = new AtomicReference<>();
        List<DailyQuestItem> storedItems = Collections.synchronizedList(
                new ArrayList<>()
        );
        when(dailyQuestMapper.findUserIdForUpdate(USER_ID))
                .thenAnswer(invocation -> {
                    userRowLock.lock();
                    return USER_ID;
                });
        when(dailyQuestMapper.findByUserIdAndQuestDateForUpdate(
                USER_ID,
                QUEST_DATE
        )).thenAnswer(invocation -> storedQuest.get());
        when(dailyQuestMapper.insertQuest(any())).thenAnswer(invocation -> {
            DailyQuest dailyQuest = invocation.getArgument(0);
            dailyQuest.setDailyQuestId(4001L);
            storedQuest.set(dailyQuest);
            return 1;
        });
        AtomicLong itemId = new AtomicLong(5000L);
        when(dailyQuestMapper.insertItem(any())).thenAnswer(invocation -> {
            DailyQuestItem item = invocation.getArgument(0);
            item.setDailyQuestItemId(itemId.incrementAndGet());
            storedItems.add(item);
            if (storedItems.size() == 5) {
                userRowLock.unlock();
            }
            return 1;
        });
        when(dailyQuestMapper.findItemsByDailyQuestId(4001L))
                .thenAnswer(invocation -> {
                    List<DailyQuestItem> result = List.copyOf(storedItems);
                    userRowLock.unlock();
                    return result;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DailyQuestAssignmentResult> first = executor.submit(() -> {
                start.await();
                return service.assignToday(USER_ID);
            });
            Future<DailyQuestAssignmentResult> second = executor.submit(() -> {
                start.await();
                return service.assignToday(USER_ID);
            });
            start.countDown();

            DailyQuestAssignmentResult firstResult = first.get(
                    5,
                    TimeUnit.SECONDS
            );
            DailyQuestAssignmentResult secondResult = second.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertEquals(
                    firstResult.dailyQuest().getDailyQuestId(),
                    secondResult.dailyQuest().getDailyQuestId()
            );
            assertEquals(
                    firstResult.items().stream()
                            .map(DailyQuestItem::getQuestionId)
                            .toList(),
                    secondResult.items().stream()
                            .map(DailyQuestItem::getQuestionId)
                            .toList()
            );
            verify(dailyQuestMapper, times(1)).insertQuest(any());
            verify(dailyQuestMapper, times(5)).insertItem(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAssignmentBeforeCurriculumConfirmation() {
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.assignToday(USER_ID)
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_NOT_AVAILABLE,
                exception.getErrorCode()
        );
        verify(quizQuestionMapper, never())
                .findLatestPublishedDailyGeneralQuestions();
        verify(dailyQuestMapper, never()).insertQuest(any());
    }

    @Test
    void rejectsAssignmentWhenTheGeneralPoolIsShort() {
        when(quizQuestionMapper
                .findLatestPublishedDailyGeneralQuestions())
                .thenReturn(List.of(
                        general(101L, "general-101", 10L, null),
                        general(102L, "general-102", 20L, null),
                        general(103L, "general-103", 30L, null)
                ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.assignToday(USER_ID)
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE,
                exception.getErrorCode()
        );
        verify(quizQuestionMapper, never())
                .findLatestPublishedDailyNewsByQuestDate(any());
        verify(dailyQuestMapper, never()).insertQuest(any());
    }

    @Test
    void rejectsAssignmentWhenTodaysPublishedNewsIsMissing() {
        when(quizQuestionMapper
                .findLatestPublishedDailyGeneralQuestions())
                .thenReturn(List.of(
                        general(101L, "general-101", 10L, null),
                        general(102L, "general-102", 20L, null),
                        general(103L, "general-103", 30L, null),
                        general(104L, "general-104", 40L, null)
                ));
        when(quizQuestionMapper
                .findLatestPublishedDailyNewsByQuestDate(QUEST_DATE))
                .thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.assignToday(USER_ID)
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE,
                exception.getErrorCode()
        );
        verify(dailyQuestMapper, never()).insertQuest(any());
    }

    private QuizQuestion general(
            long questionId,
            String questionKey,
            long mainChapterId,
            Long subChapterId
    ) {
        QuizQuestion question = baseQuestion(questionId, questionKey);
        question.setUsageType(QuizUsageType.DAILY_GENERAL);
        question.setMainChapterId(mainChapterId);
        question.setSubChapterId(subChapterId);
        question.setGenerationType(QuizGenerationType.HUMAN);
        return question;
    }

    private QuizQuestion news(long questionId, String questionKey) {
        QuizQuestion question = baseQuestion(questionId, questionKey);
        question.setUsageType(QuizUsageType.DAILY_NEWS);
        question.setQuestionType(QuizQuestionType.SCENARIO);
        question.setGenerationType(QuizGenerationType.AI);
        question.setQuestDate(QUEST_DATE);
        question.setPrompt("오늘의 뉴스 문제");
        question.setScenarioJson("{\"situation\":\"금리가 올랐다\"}");
        question.setSourceRefsJson("[{\"url\":\"https://example.com\"}]");
        return question;
    }

    private QuizQuestion baseQuestion(long questionId, String questionKey) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(questionId);
        question.setQuestionKey(questionKey);
        question.setVersionNo(1);
        question.setQuestionType(QuizQuestionType.SINGLE_CHOICE);
        question.setPrompt("일반 문제 " + questionId);
        question.setOptionsJson("""
                [
                  {"key":"A","label":"선택지 A"},
                  {"key":"B","label":"선택지 B"}
                ]
                """);
        question.setCorrectAnswerJson("{\"key\":\"A\"}");
        question.setExplanation("정답 해설");
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private UserCurriculumItem curriculumItem() {
        UserCurriculumItem item = new UserCurriculumItem();
        item.setUserId(USER_ID);
        item.setMainChapterId(1L);
        return item;
    }

    private List<DailyQuestItem> storedItems(long dailyQuestId) {
        return java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(order -> {
                    DailyQuestItem item = DailyQuestItem.assigned(
                            dailyQuestId,
                            100L + order,
                            order,
                            "{\"prompt\":\"stored\"}",
                            CREATED_AT
                    );
                    item.setDailyQuestItemId(5000L + order);
                    return item;
                })
                .toList();
    }
}
