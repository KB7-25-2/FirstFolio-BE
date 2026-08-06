package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.curriculum.domain.SubChapter;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SubChapterMapper {

    List<SubChapter> findAllByMainChapterId(
            @Param("mainChapterId") long mainChapterId
    );

    SubChapter findById(@Param("subChapterId") long subChapterId);

    int countDisplayOrderConflict(
            @Param("mainChapterId") long mainChapterId,
            @Param("displayOrder") int displayOrder,
            @Param("excludedSubChapterId") Long excludedSubChapterId
    );

    int insert(SubChapter chapter);

    int updateMetadata(
            @Param("subChapterId") long subChapterId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("displayOrder") int displayOrder,
            @Param("active") boolean active,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
