package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MainChapterMapper {

    List<MainChapter> findAll(
            @Param("chapterType") ChapterType chapterType,
            @Param("active") Boolean active
    );

    MainChapter findById(@Param("mainChapterId") long mainChapterId);

    int countActiveByChapterType(@Param("chapterType") ChapterType chapterType);

    int insert(MainChapter chapter);

    int updateMetadata(
            @Param("mainChapterId") long mainChapterId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("displayOrder") int displayOrder,
            @Param("active") boolean active,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
