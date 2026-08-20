package org.firstfolio.newsletter.dto.request;

import java.time.LocalDate;
import java.util.List;

/**
 * 내부 뉴스레터 등록 요청 ({@code POST /api/internal/newsletters}).
 *
 * <p>{@code financialWordsJson}·{@code issuesJson}·{@code statsJson}은 각각
 * 정확히 3개여야 한다 ({@code NewsletterCreateService} 검증).</p>
 */
public record NewsletterCreateRequest(
        LocalDate weekStartDate,
        String headline,
        List<FinancialWordRequest> financialWordsJson,
        List<NewsletterIssueRequest> issuesJson,
        List<NewsletterStatRequest> statsJson
) {
}
