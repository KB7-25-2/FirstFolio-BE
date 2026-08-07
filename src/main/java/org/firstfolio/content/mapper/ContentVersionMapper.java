package org.firstfolio.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.content.domain.ContentVersion;

@Mapper
public interface ContentVersionMapper {

    int countBySubChapterIdAndVersionNo(
            @Param("subChapterId") long subChapterId,
            @Param("versionNo") int versionNo
    );

    int insert(ContentVersion contentVersion);
}
