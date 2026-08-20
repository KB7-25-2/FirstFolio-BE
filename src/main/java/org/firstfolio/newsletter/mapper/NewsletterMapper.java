package org.firstfolio.newsletter.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterStatus;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface NewsletterMapper {

    Newsletter findById(@Param("newsletterId") long newsletterId);

    List<Newsletter> findByStatus(@Param("status") NewsletterStatus status);

    List<Newsletter> findByWeekStartDate(@Param("weekStartDate") LocalDate weekStartDate);

    int publishReview(
            @Param("newsletterId") long newsletterId,
            @Param("publishedAt") java.time.LocalDateTime publishedAt
    );

    int retirePublished(@Param("newsletterId") long newsletterId);

    int insert(Newsletter newsletter);
}
