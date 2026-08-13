package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestAssignmentResult;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestTodayResult;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestTodayQueryServiceTest {

    private static final long USER_ID = 10L;
    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 13);

    private DailyQuestAssignmentService assignmentService;
    private DailyQuestTodayQueryService service;

    @BeforeEach
    void setUp() {
        assignmentService = mock(DailyQuestAssignmentService.class);
        service = new DailyQuestTodayQueryService(assignmentService);
    }

    @Test
    void assignsOrRestoresTodayAndConvertsStoredAnswers() {
        when(assignmentService.assignToday(USER_ID)).thenReturn(
                assignment("{\"key\":\"B\"}")
        );

        DailyQuestTodayResult result = service.getToday(USER_ID);

        assertEquals(4001L, result.dailyQuest().getDailyQuestId());
        assertEquals(1, result.answeredCount());
        assertEquals(5, result.questions().size());
        assertEquals(5001L, result.questions().get(0).dailyQuestItemId());
        assertEquals(101L, result.questions().get(0).questionId());
        assertEquals("B", result.questions().get(0).savedAnswerKey());
        assertNull(result.questions().get(1).savedAnswerKey());
        assertEquals(
                List.of("A", "B"),
                result.questions().get(0).choices().stream()
                        .map(choice -> choice.key())
                        .toList()
        );
        verify(assignmentService).assignToday(USER_ID);
    }

    @Test
    void rejectsBrokenStoredAnswerInsteadOfReturningIt() {
        when(assignmentService.assignToday(USER_ID)).thenReturn(
                assignment("{\"key\":\"Z\"}")
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getToday(USER_ID)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    private DailyQuestAssignmentResult assignment(String firstAnswer) {
        DailyQuest dailyQuest = DailyQuest.assigned(USER_ID, QUEST_DATE);
        dailyQuest.setDailyQuestId(4001L);
        List<DailyQuestItem> items = java.util.stream.IntStream
                .rangeClosed(1, 5)
                .mapToObj(order -> item(order, order == 1 ? firstAnswer : null))
                .toList();
        return new DailyQuestAssignmentResult(dailyQuest, items);
    }

    private DailyQuestItem item(int order, String answer) {
        DailyQuestItem item = DailyQuestItem.assigned(
                4001L,
                100L + order,
                order,
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
                          "explanation":"응답에 노출되면 안 되는 해설",
                          "source_refs_json":null
                        }
                        """,
                LocalDateTime.of(2026, 8, 13, 0, 0)
        );
        item.setDailyQuestItemId(5000L + order);
        item.setUserAnswerJson(answer);
        return item;
    }
}
