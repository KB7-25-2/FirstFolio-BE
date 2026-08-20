package org.firstfolio.newsletter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterStatus;
import org.firstfolio.newsletter.dto.response.NewsletterDetailResponse;
import org.firstfolio.newsletter.dto.response.NewsletterListResponse;
import org.firstfolio.newsletter.mapper.NewsletterMapper;
import org.springframework.stereotype.Service;

@Service
public class NewsletterQueryService {

    private final NewsletterMapper newsletterMapper;
    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public NewsletterQueryService(NewsletterMapper newsletterMapper) {
        this.newsletterMapper = newsletterMapper;
    }

    public NewsletterListResponse findByStatus(NewsletterStatus status) {
        return new NewsletterListResponse(
                newsletterMapper.findByStatus(status).stream()
                        .map(newsletter -> NewsletterDetailResponse.from(newsletter, objectMapper))
                        .toList()
        );
    }

    public NewsletterDetailResponse findById(long newsletterId) {
        Newsletter newsletter = newsletterMapper.findById(newsletterId);
        if (newsletter == null) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_FOUND);
        }
        return NewsletterDetailResponse.from(newsletter, objectMapper);
    }
}
