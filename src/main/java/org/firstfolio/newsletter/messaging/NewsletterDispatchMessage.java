package org.firstfolio.newsletter.messaging;

import org.firstfolio.newsletter.dto.response.FinancialWordResponse;
import org.firstfolio.newsletter.dto.response.NewsletterIssueResponse;
import org.firstfolio.newsletter.dto.response.NewsletterStatResponse;

import java.util.List;

/**
 * RabbitMQ로 발행하는 뉴스레터 발송 작업 메시지.
 *
 * <p>구독자 목록을 통째로 담는다 — 지금 사용자 규모에서는 워커가 매번 BE에 구독자를
 * 되물어보는 것보다 간단하다. 사용자 수가 크게 늘면 그때 newsletterId만 담고 워커가
 * 조회하는 방식으로 바꾼다.</p>
 */
public record NewsletterDispatchMessage(
        long newsletterId,
        String headline,
        List<FinancialWordResponse> financialWords,
        List<NewsletterIssueResponse> issues,
        List<NewsletterStatResponse> stats,
        List<String> recipients
) {
}
