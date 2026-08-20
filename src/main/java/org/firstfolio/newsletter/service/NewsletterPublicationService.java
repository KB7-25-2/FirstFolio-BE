package org.firstfolio.newsletter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterStatus;
import org.firstfolio.newsletter.mapper.NewsletterMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NewsletterPublicationService {

    private static final String AUDIT_ENTITY_TYPE = "NEWSLETTER";

    private final NewsletterMapper newsletterMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public NewsletterPublicationService(
            NewsletterMapper newsletterMapper,
            AdminAuditLogMapper auditLogMapper,
            Clock clock
    ) {
        this.newsletterMapper = newsletterMapper;
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    @Transactional
    public Newsletter publish(long newsletterId, long actorUserId, String requestId) {
        Newsletter target = newsletterMapper.findByIdForUpdate(newsletterId);
        if (target == null) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_FOUND);
        }
        if (target.getStatus() != NewsletterStatus.REVIEW) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_PUBLISHABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String before = snapshot(target);
        if (newsletterMapper.publishReview(newsletterId, now) != 1) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_PUBLISHABLE);
        }
        target.publish(now);
        insertAuditLog(actorUserId, "PUBLISH", target, before, requestId, now);
        return target;
    }

    @Transactional
    public Newsletter retire(long newsletterId, long actorUserId, String requestId) {
        Newsletter target = newsletterMapper.findByIdForUpdate(newsletterId);
        if (target == null) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_FOUND);
        }
        if (target.getStatus() != NewsletterStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_RETIRABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String before = snapshot(target);
        if (newsletterMapper.retirePublished(newsletterId) != 1) {
            throw new ApiException(ErrorCode.NEWSLETTER_NOT_RETIRABLE);
        }
        target.retire();
        insertAuditLog(actorUserId, "RETIRE", target, before, requestId, now);
        return target;
    }

    private void insertAuditLog(
            long actorUserId,
            String action,
            Newsletter newsletter,
            String before,
            String requestId,
            LocalDateTime now
    ) {
        if (auditLogMapper.insert(
                actorUserId,
                action,
                AUDIT_ENTITY_TYPE,
                newsletter.getNewsletterId(),
                before,
                snapshot(newsletter),
                requestId,
                now
        ) != 1) {
            throw new IllegalStateException("뉴스레터 상태 변경 감사 이력을 저장하지 못했습니다.");
        }
    }

    private String snapshot(Newsletter newsletter) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("newsletter_id", newsletter.getNewsletterId());
        snapshot.put("week_start_date", newsletter.getWeekStartDate());
        snapshot.put("headline", newsletter.getHeadline());
        snapshot.put("status", newsletter.getStatus());
        snapshot.put("generation_type", newsletter.getGenerationType());
        snapshot.put("published_at", newsletter.getPublishedAt());
        snapshot.put("created_by", newsletter.getCreatedBy());
        snapshot.put("created_at", newsletter.getCreatedAt());

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("뉴스레터 감사 이력을 직렬화할 수 없습니다.", exception);
        }
    }
}
