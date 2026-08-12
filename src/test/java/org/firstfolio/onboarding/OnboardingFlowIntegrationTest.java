package org.firstfolio.onboarding;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.controller.CurriculumConfirmController;
import org.firstfolio.curriculum.controller.CurriculumController;
import org.firstfolio.curriculum.controller.CurriculumDraftController;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumOverviewItem;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.curriculum.service.CurriculumConfirmService;
import org.firstfolio.curriculum.service.CurriculumDraftService;
import org.firstfolio.curriculum.service.UserCurriculumQueryService;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.quiz.controller.LevelTestAnswerController;
import org.firstfolio.quiz.controller.LevelTestAttemptController;
import org.firstfolio.quiz.controller.LevelTestSubmitController;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.firstfolio.quiz.service.LevelTestAnswerSaveService;
import org.firstfolio.quiz.service.LevelTestAttemptStartService;
import org.firstfolio.quiz.service.LevelTestQueryService;
import org.firstfolio.quiz.service.LevelTestSubmitService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingFlowIntegrationTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 2001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T04:00:00Z"),
            ZoneOffset.UTC
    );

    private final List<QuizAnswer> answers = new ArrayList<>();
    private final List<UserCurriculumItem> curriculum = new ArrayList<>();
    private final AtomicInteger attemptInsertCount = new AtomicInteger();
    private final AtomicInteger curriculumInsertCount = new AtomicInteger();

    private QuizAttempt attempt;
    private MainChapter foundation;
    private MainChapter deposit;
    private MainChapter bond;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        foundation = chapter(
                1L,
                "포트폴리오 기초",
                ChapterType.FOUNDATION,
                null,
                1,
                true
        );
        deposit = chapter(
                2L,
                "예·적금",
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                2,
                false
        );
        bond = chapter(
                3L,
                "채권",
                ChapterType.ASSET,
                AssetType.BOND,
                3,
                false
        );

        MainChapterMapper mainChapterMapper = mainChapterMapper();
        QuizQuestionMapper questionMapper = questionMapper();
        QuizAttemptMapper attemptMapper = attemptMapper();
        UserCurriculumMapper curriculumMapper = curriculumMapper();

        LevelTestQueryService levelTestQueryService =
                new LevelTestQueryService(
                        mainChapterMapper,
                        questionMapper,
                        attemptMapper
                );
        LevelTestAttemptStartService startService =
                new LevelTestAttemptStartService(
                        attemptMapper,
                        levelTestQueryService,
                        CLOCK
                );
        LevelTestAnswerSaveService answerSaveService =
                new LevelTestAnswerSaveService(attemptMapper, CLOCK);
        LevelTestSubmitService submitService =
                new LevelTestSubmitService(attemptMapper, CLOCK);
        CurriculumDraftService draftService = new CurriculumDraftService(
                mainChapterMapper,
                submitService
        );
        CurriculumConfirmService confirmService =
                new CurriculumConfirmService(
                        curriculumMapper,
                        draftService,
                        CLOCK
                );
        UserCurriculumQueryService queryService =
                new UserCurriculumQueryService(
                        curriculumMapper,
                        mainChapterMapper
                );

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LevelTestAttemptController(startService),
                        new LevelTestAnswerController(answerSaveService),
                        new LevelTestSubmitController(submitService),
                        new CurriculumDraftController(draftService),
                        new CurriculumConfirmController(confirmService),
                        new CurriculumController(queryService)
                )
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void completesLevelTestThroughConfirmedCurriculumQuery() throws Exception {
        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_id").value(ATTEMPT_ID))
                .andExpect(jsonPath("$.data.question_count").value(3));

        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/{attemptId}/answers",
                        ATTEMPT_ID
                )).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "answers": [
                            {"question_id":1001,"answer":{"key":"B"}},
                            {"question_id":1002,"answer":{"key":"A"}},
                            {"question_id":1003,"answer":{"key":"B"}}
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered_count").value(3))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/{attemptId}/submit",
                        ATTEMPT_ID
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath(
                        "$.data.chapter_results[0].all_correct"
                ).value(false))
                .andExpect(jsonPath(
                        "$.data.chapter_results[1].all_correct"
                ).value(true));

        mockMvc.perform(authenticated(get("/api/curriculum/draft")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].main_chapter_id")
                        .value(2))
                .andExpect(jsonPath(
                        "$.data.cart_candidates[0].main_chapter_id"
                ).value(3));

        mockMvc.perform(authenticated(put("/api/curriculum/draft"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[3,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].main_chapter_id")
                        .value(3))
                .andExpect(jsonPath("$.data.items[1].source_type")
                        .value("USER_ADDED"))
                .andExpect(jsonPath("$.data.items[2].source_type")
                        .value("LEVEL_TEST_WRONG"));

        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[3,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3));

        mockMvc.perform(authenticated(get("/api/curriculum")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].chapter_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[1].main_chapter_id")
                        .value(3))
                .andExpect(jsonPath("$.data.items[2].main_chapter_id")
                        .value(2))
                .andExpect(jsonPath("$.data.items[2].display_order")
                        .value(3))
                .andExpect(jsonPath("$.data.items[2].progress_percent")
                        .value(0));
    }

    @Test
    void coversReentryAnswerChangesMissingAndDuplicateRequests()
            throws Exception {
        mockMvc.perform(authenticated(get("/api/curriculum")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("CURRICULUM_NOT_FOUND"));

        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_id").value(ATTEMPT_ID));
        assertEquals(1, attemptInsertCount.get());

        saveAnswer(1001L, "A");
        saveAnswer(1001L, "B");
        assertEquals("{\"key\":\"B\"}", answers.get(0).getUserAnswerJson());
        assertNull(answers.get(0).getCorrect());

        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/{attemptId}/submit",
                        ATTEMPT_ID
                )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("REQUIRED_ANSWERS_MISSING"));

        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/{attemptId}/answers",
                        ATTEMPT_ID
                )).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "answers": [
                            {"question_id":1002,"answer":{"key":"A"}},
                            {"question_id":1003,"answer":{"key":"B"}}
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered_count").value(3));

        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/{attemptId}/submit",
                        ATTEMPT_ID
                )))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/{attemptId}/submit",
                        ATTEMPT_ID
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"));

        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("LEVEL_TEST_ALREADY_COMPLETED"));
        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/{attemptId}/answers",
                        ATTEMPT_ID
                )).contentType(MediaType.APPLICATION_JSON).content("""
                        {"answers":[
                          {"question_id":1001,"answer":{"key":"A"}}
                        ]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("ATTEMPT_ALREADY_GRADED"));

        String selection = "{\"main_chapter_ids\":[2]}";
        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection))
                .andExpect(status().isOk());
        assertEquals(1, curriculumInsertCount.get());

        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[3,2]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("CURRICULUM_ALREADY_CONFIRMED"));
    }

    @Test
    void confirmsAndReadsFoundationOnlyCurriculum() throws Exception {
        completeAllCorrectLevelTest();

        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].source_type")
                        .value("FOUNDATION"));

        mockMvc.perform(authenticated(get("/api/curriculum")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].chapter_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[0].display_order")
                        .value(1));
    }

    private void completeAllCorrectLevelTest() throws Exception {
        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isCreated());
        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/{attemptId}/answers",
                        ATTEMPT_ID
                )).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "answers": [
                            {"question_id":1001,"answer":{"key":"B"}},
                            {"question_id":1002,"answer":{"key":"B"}},
                            {"question_id":1003,"answer":{"key":"B"}}
                          ]
                        }
                        """))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/{attemptId}/submit",
                        ATTEMPT_ID
                )))
                .andExpect(status().isOk());
    }

    private void saveAnswer(long questionId, String key) throws Exception {
        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/{attemptId}/answers",
                        ATTEMPT_ID
                )).contentType(MediaType.APPLICATION_JSON).content("""
                        {"answers":[
                          {"question_id":%d,"answer":{"key":"%s"}}
                        ]}
                        """.formatted(questionId, key)))
                .andExpect(status().isOk());
    }

    private MainChapterMapper mainChapterMapper() {
        MainChapterMapper mapper = mock(MainChapterMapper.class);
        when(mapper.findAll(ChapterType.FOUNDATION, true))
                .thenReturn(List.of(foundation));
        when(mapper.findAll(ChapterType.ASSET, true))
                .thenReturn(List.of(deposit, bond));
        return mapper;
    }

    private QuizQuestionMapper questionMapper() {
        QuizQuestionMapper mapper = mock(QuizQuestionMapper.class);
        when(mapper.findLatestPublishedLevelTestQuestions()).thenReturn(
                List.of(
                        question(1001L, 2L, 1),
                        question(1002L, 2L, 2),
                        question(1003L, 3L, 1)
                )
        );
        return mapper;
    }

    private QuizAttemptMapper attemptMapper() {
        QuizAttemptMapper mapper = mock(QuizAttemptMapper.class);
        when(mapper.findUserIdForUpdate(USER_ID)).thenReturn(USER_ID);
        when(mapper.findLevelTestByUserIdForUpdate(USER_ID))
                .thenAnswer(ignored -> attempt);
        when(mapper.findLevelTestByUserId(USER_ID))
                .thenAnswer(ignored -> attempt);
        when(mapper.insertAttempt(any())).thenAnswer(invocation -> {
            attempt = invocation.getArgument(0);
            attempt.setAttemptId(ATTEMPT_ID);
            attemptInsertCount.incrementAndGet();
            return 1;
        });
        when(mapper.insertAnswer(any())).thenAnswer(invocation -> {
            QuizAnswer answer = invocation.getArgument(0);
            answer.setQuizAnswerId(3000L + answers.size() + 1);
            answers.add(answer);
            return 1;
        });
        when(mapper.findAnswersByAttemptId(ATTEMPT_ID))
                .thenAnswer(ignored -> List.copyOf(answers));
        when(mapper.findAnswersByAttemptIdForUpdate(ATTEMPT_ID))
                .thenAnswer(ignored -> List.copyOf(answers));
        when(mapper.findByIdForUpdate(ATTEMPT_ID))
                .thenAnswer(ignored -> attempt);
        when(mapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                eq(ATTEMPT_ID),
                anyLong()
        )).thenAnswer(invocation -> {
            long questionId = invocation.getArgument(1);
            return answers.stream()
                    .filter(answer -> answer.getQuestionId() == questionId)
                    .findFirst()
                    .orElse(null);
        });
        when(mapper.saveLevelTestAnswer(any())).thenReturn(1);
        when(mapper.countAnsweredByAttemptId(ATTEMPT_ID)).thenAnswer(
                ignored -> (int) answers.stream()
                        .filter(answer -> answer.getUserAnswerJson() != null)
                        .count()
        );
        when(mapper.gradeLevelTestAnswer(any())).thenReturn(1);
        when(mapper.completeAttemptIfInProgress(any())).thenReturn(1);
        return mapper;
    }

    private UserCurriculumMapper curriculumMapper() {
        UserCurriculumMapper mapper = mock(UserCurriculumMapper.class);
        when(mapper.findUserIdForUpdate(USER_ID)).thenReturn(USER_ID);
        when(mapper.findActiveByUserId(USER_ID))
                .thenAnswer(ignored -> List.copyOf(curriculum));
        when(mapper.insertAll(
                eq(USER_ID),
                anyList(),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<CurriculumDraftItem> items = invocation.getArgument(1);
            LocalDateTime confirmedAt = invocation.getArgument(2);
            for (CurriculumDraftItem item : items) {
                UserCurriculumItem saved = new UserCurriculumItem();
                saved.setCurriculumItemId(5000L + curriculum.size() + 1);
                saved.setUserId(USER_ID);
                saved.setMainChapterId(item.mainChapterId());
                saved.setDisplayOrder(item.displayOrder());
                saved.setSourceType(item.sourceType());
                saved.setStatus(CurriculumItemStatus.ACTIVE);
                saved.setConfirmedAt(confirmedAt);
                curriculum.add(saved);
            }
            curriculumInsertCount.incrementAndGet();
            return items.size();
        });
        when(mapper.findOverviewByUserId(USER_ID)).thenAnswer(ignored ->
                curriculum.stream().map(saved -> {
                    MainChapter chapter = chapter(saved.getMainChapterId());
                    return new CurriculumOverviewItem(
                            saved.getCurriculumItemId(),
                            saved.getMainChapterId(),
                            chapter.getTitle(),
                            chapter.getChapterType(),
                            saved.getDisplayOrder(),
                            saved.getSourceType(),
                            saved.getStatus(),
                            saved.getCompletedAt(),
                            saved.getCompletedAt() == null ? 0 : 100
                    );
                }).toList()
        );
        return mapper;
    }

    private MainChapter chapter(long mainChapterId) {
        if (mainChapterId == foundation.getMainChapterId()) {
            return foundation;
        }
        if (mainChapterId == deposit.getMainChapterId()) {
            return deposit;
        }
        if (mainChapterId == bond.getMainChapterId()) {
            return bond;
        }
        throw new IllegalArgumentException("unknown main chapter");
    }

    private MainChapter chapter(
            long id,
            String title,
            ChapterType chapterType,
            AssetType assetType,
            int displayOrder,
            boolean required
    ) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setTitle(title);
        chapter.setChapterType(chapterType);
        chapter.setAssetType(assetType);
        chapter.setDisplayOrder(displayOrder);
        chapter.setRequired(required);
        chapter.setActive(true);
        return chapter;
    }

    private QuizQuestion question(
            long questionId,
            long mainChapterId,
            int displayOrder
    ) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(questionId);
        question.setQuestionKey("level-test-" + questionId);
        question.setVersionNo(1);
        question.setUsageType(QuizUsageType.LEVEL_TEST);
        question.setMainChapterId(mainChapterId);
        question.setDisplayOrder(displayOrder);
        question.setQuestionType(QuizQuestionType.SINGLE_CHOICE);
        question.setGenerationType(QuizGenerationType.HUMAN);
        question.setPrompt("레벨 테스트 문제 " + questionId);
        question.setOptionsJson("""
                [
                  {"key":"A","label":"선택지 A"},
                  {"key":"B","label":"선택지 B"}
                ]
                """);
        question.setCorrectAnswerJson("{\"key\":\"B\"}");
        question.setExplanation("정답 해설");
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder builder
    ) {
        return builder.requestAttr(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(
                        USER_ID,
                        "firebase-uid",
                        "학습자",
                        UserRole.USER
                )
        );
    }
}
