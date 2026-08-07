package org.firstfolio.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.content.domain.ContentVersion;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ContentVersionMapper {

    List<ContentVersion> findAllBySubChapterId(
            @Param("subChapterId") long subChapterId
    );

    ContentVersion findById(@Param("contentVersionId") long contentVersionId);

    ContentVersion findByIdForUpdate(
            @Param("contentVersionId") long contentVersionId
    );

    int countBySubChapterIdAndVersionNo(
            @Param("subChapterId") long subChapterId,
            @Param("versionNo") int versionNo
    );

    int insert(ContentVersion contentVersion);

    int publishDraft(
            @Param("contentVersionId") long contentVersionId,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    int retirePublished(@Param("contentVersionId") long contentVersionId);
}
