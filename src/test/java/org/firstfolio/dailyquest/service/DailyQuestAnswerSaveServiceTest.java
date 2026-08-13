package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestAnswerSaveResult;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.mapper.DailyQuestMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestAnswerSaveServiceTest {

    private static final long USER_ID = 10L;
    private static final long QUEST_ID = 4001L;
    private static final long ITEM_ID = 5001L;
    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 13);
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            13,
            1,
            0
    );

    private DailyQuestMapper mapper;
    private DailyQuestAnswerSaveService service;
    private DailyQuest dailyQuest;
    private DailyQuestItem item;

    @BeforeEach
    void setUp() {
        mapper = mock(DailyQuestMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-13T01:00:00Z"),
                ZoneOffset.UTC
        );
        service = new DailyQuestAnswerSaveService(mapper, clock);

        dailyQuest = DailyQuest.assigned(USER_ID, QUEST_DATE);
        dailyQuest.setDailyQuestId(QUEST_ID);
        dailyQuest.setStatus(DailyQuestStatus.IN_PROGRESS);
        item = item(null);

        when(mapper.findQuestIdByItemIdAndUserId(ITEM_ID, USER_ID))
                .thenReturn(QUEST_ID);
        when(mapper.findByIdForUpdate(QUEST_ID)).thenReturn(dailyQuest);
        when(mapper.findItemByIdAndUserIdForUpdate(ITEM_ID, USER_ID))
                .thenReturn(item);
        when(mapper.saveAnswer(any())).thenReturn(1);
        when(mapper.countItemsByDailyQuestId(QUEST_ID)).thenReturn(5);
        when(mapper.countAnsweredItemsByDailyQuestId(QUEST_ID))
                .thenReturn(1);
    }

    @Test
    void savesAnswerWithoutGrading() {
        DailyQuestAnswerSaveResult result = service.save(
                USER_ID,
                ITEM_ID,
                " B "
        );

        assertEquals(QUEST_ID, result.dailyQuestId());
        assertEquals(ITEM_ID, result.dailyQuestItemId());
        assertEquals("B", result.savedAnswerKey());
        assertEquals(1, result.answeredCount());
        assertEquals(5, result.totalCount());

        ArgumentCaptor<DailyQuestItem> captor =
                ArgumentCaptor.forClass(DailyQuestItem.class);
        verify(mapper).saveAnswer(captor.capture());
        DailyQuestItem saved = captor.getValue();
        assertEquals("{\"key\":\"B\"}", saved.getUserAnswerJson());
        assertEquals(NOW, saved.getAnsweredAt());
        assertNull(saved.getCorrect());
        verify(mapper, never()).markInProgressIfAssigned(QUEST_ID);
    }

    @Test
    void overwritesAChangedAnswerBeforeCompletion() {
        item.setUserAnswerJson("{\"key\":\"A\"}");
        when(mapper.countAnsweredItemsByDailyQuestId(QUEST_ID))
                .thenReturn(2);

        DailyQuestAnswerSaveResult result = service.save(
                USER_ID,
                ITEM_ID,
                "B"
        );

        assertEquals("B", result.savedAnswerKey());
        assertEquals(2, result.answeredCount());
        verify(mapper).saveAnswer(item);
        assertEquals("{\"key\":\"B\"}", item.getUserAnswerJson());
    }

    @Test
    void returnsExistingResultForTheSameAnswerWithoutWritingAgain() {
        item.setUserAnswerJson("{\"key\":\"B\"}");

        DailyQuestAnswerSaveResult result = service.save(
                USER_ID,
                ITEM_ID,
                "B"
        );

        assertEquals("B", result.savedAnswerKey());
        verify(mapper, never()).saveAnswer(any());
    }

    @Test
    void movesAssignedQuestToInProgressOnFirstSave() {
        dailyQuest.setStatus(DailyQuestStatus.ASSIGNED);
        when(mapper.markInProgressIfAssigned(QUEST_ID)).thenReturn(1);

        service.save(USER_ID, ITEM_ID, "A");

        verify(mapper).markInProgressIfAssigned(QUEST_ID);
    }

    @Test
    void rejectsCompletedQuestBeforeChangingTheAnswer() {
        dailyQuest.setStatus(DailyQuestStatus.COMPLETED);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, ITEM_ID, "A")
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_ALREADY_COMPLETED,
                exception.getErrorCode()
        );
        verify(mapper, never()).findItemByIdAndUserIdForUpdate(
                ITEM_ID,
                USER_ID
        );
        verify(mapper, never()).saveAnswer(any());
    }

    @Test
    void hidesAnItemThatDoesNotBelongToTheUser() {
        when(mapper.findQuestIdByItemIdAndUserId(ITEM_ID, USER_ID))
                .thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, ITEM_ID, "A")
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_ITEM_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(mapper, never()).findByIdForUpdate(QUEST_ID);
    }

    @Test
    void rejectsAnItemFromAnotherDateAsNotFoundToday() {
        dailyQuest.setQuestDate(QUEST_DATE.minusDays(1));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, ITEM_ID, "A")
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_ITEM_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(mapper, never()).saveAnswer(any());
    }

    @Test
    void rejectsMissingOrUnknownChoiceKey() {
        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, ITEM_ID, null)
        );
        ApiException unknown = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, ITEM_ID, "Z")
        );

        assertEquals(ErrorCode.INVALID_ANSWER, missing.getErrorCode());
        assertEquals(ErrorCode.INVALID_ANSWER, unknown.getErrorCode());
        verify(mapper, never()).saveAnswer(any());
    }

    private DailyQuestItem item(String answer) {
        DailyQuestItem dailyQuestItem = DailyQuestItem.assigned(
                QUEST_ID,
                1001L,
                1,
                """
                        {
                          "question_type":"SINGLE_CHOICE",
                          "generation_type":"HUMAN",
                          "prompt":"일일 문제",
                          "scenario_json":null,
                          "options_json":[
                            {"key":"A","label":"선택지 A"},
                            {"key":"B","label":"선택지 B"}
                          ],
                          "correct_answer_json":{"key":"A"},
                          "explanation":"정답 해설"
                        }
                        """,
                NOW.minusHours(1)
        );
        dailyQuestItem.setDailyQuestItemId(ITEM_ID);
        dailyQuestItem.setUserAnswerJson(answer);
        return dailyQuestItem;
    }
}
