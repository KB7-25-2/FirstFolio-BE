package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCurriculumQueryServiceTest {

    private UserCurriculumMapper userCurriculumMapper;
    private MainChapterMapper mainChapterMapper;
    private UserCurriculumQueryService service;

    @BeforeEach
    void setUp() {
        userCurriculumMapper = mock(UserCurriculumMapper.class);
        mainChapterMapper = mock(MainChapterMapper.class);
        service = new UserCurriculumQueryService(
                userCurriculumMapper,
                mainChapterMapper
        );
    }

    @Test
    void returnsEmptyWhenCurriculumIsNotConfirmed() {
        when(userCurriculumMapper.findActiveByUserId(11L))
                .thenReturn(List.of());

        assertEquals(List.of(), service.findConfirmedCurriculum(11L));
        verify(mainChapterMapper, never()).findAll(ChapterType.FOUNDATION, true);
    }

    @Test
    void returnsCurriculumWhoseFirstItemIsRequiredFoundation() {
        MainChapter foundation = foundation(1L);
        UserCurriculumItem required = item(
                1L,
                1,
                CurriculumSourceType.FOUNDATION
        );
        UserCurriculumItem selected = item(
                2L,
                2,
                CurriculumSourceType.LEVEL_TEST_WRONG
        );
        when(userCurriculumMapper.findActiveByUserId(11L))
                .thenReturn(List.of(required, selected));
        when(mainChapterMapper.findAll(ChapterType.FOUNDATION, true))
                .thenReturn(List.of(foundation));

        List<UserCurriculumItem> result = service.findConfirmedCurriculum(11L);

        assertSame(required, result.get(0));
        assertSame(selected, result.get(1));
    }

    @Test
    void rejectsCurriculumWithoutRequiredFoundationFirst() {
        when(userCurriculumMapper.findActiveByUserId(11L))
                .thenReturn(List.of(item(
                        2L,
                        1,
                        CurriculumSourceType.USER_ADDED
                )));
        when(mainChapterMapper.findAll(ChapterType.FOUNDATION, true))
                .thenReturn(List.of(foundation(1L)));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findConfirmedCurriculum(11L)
        );

        assertEquals(
                ErrorCode.CURRICULUM_CONFIGURATION_INVALID,
                exception.getErrorCode()
        );
    }

    private MainChapter foundation(long id) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setChapterType(ChapterType.FOUNDATION);
        chapter.setRequired(true);
        chapter.setActive(true);
        return chapter;
    }

    private UserCurriculumItem item(
            long mainChapterId,
            int displayOrder,
            CurriculumSourceType sourceType
    ) {
        UserCurriculumItem item = new UserCurriculumItem();
        item.setUserId(11L);
        item.setMainChapterId(mainChapterId);
        item.setDisplayOrder(displayOrder);
        item.setSourceType(sourceType);
        item.setStatus(CurriculumItemStatus.ACTIVE);
        return item;
    }
}
