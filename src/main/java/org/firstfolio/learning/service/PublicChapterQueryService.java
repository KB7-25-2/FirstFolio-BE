package org.firstfolio.learning.service;

import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.PublicSubChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublicChapterQueryService {

    private final MainChapterMapper mainChapterMapper;
    private final SubChapterMapper subChapterMapper;

    public PublicChapterQueryService(
            MainChapterMapper mainChapterMapper,
            SubChapterMapper subChapterMapper
    ) {
        this.mainChapterMapper = mainChapterMapper;
        this.subChapterMapper = subChapterMapper;
    }

    @Transactional(readOnly = true)
    public List<MainChapter> getMainChapters() {
        return mainChapterMapper.findAll(null, true);
    }

    @Transactional(readOnly = true)
    public List<PublicSubChapter> getSubChapters(long mainChapterId) {
        MainChapter mainChapter = mainChapterMapper.findById(mainChapterId);
        if (mainChapter == null || !mainChapter.isActive()) {
            throw new ApiException(ErrorCode.MAIN_CHAPTER_NOT_FOUND);
        }
        return subChapterMapper.findPublicByMainChapterId(mainChapterId);
    }
}
