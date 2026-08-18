package org.firstfolio.news.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.news.domain.NewsArticle;

import java.util.List;

@Mapper
public interface NewsMapper {

    List<NewsArticle> findLatest(@Param("limit") int limit);
}
