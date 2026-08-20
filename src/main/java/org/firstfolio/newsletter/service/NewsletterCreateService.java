package org.firstfolio.newsletter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterGenerationType;
import org.firstfolio.newsletter.dto.request.FinancialWordRequest;
import org.firstfolio.newsletter.dto.request.NewsletterCreateRequest;
import org.firstfolio.newsletter.dto.request.NewsletterIssueRequest;
import org.firstfolio.newsletter.dto.request.NewsletterStatRequest;
import org.firstfolio.newsletter.mapper.NewsletterMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 배치 전용 시스템 사용자 ID({@code newsletter.batch.ai-created-by})는 관리자 계정이
 * 정해지기 전까지 비어 있을 수 있다. 호출 시점에 읽어서, 값이 없어도 서버 기동 자체는
 * 막지 않는다 ({@code QuizQuestionBatchWriter}와 동일한 이유).
 */
@Service
public class NewsletterCreateService {

    private static final String AI_CREATED_BY_PROPERTY = "newsletter.batch.ai-created-by";
    private static final int REQUIRED_SECTION_SIZE = 3;

    private final NewsletterMapper newsletterMapper;
    private final Environment environment;
    private final Clock clock;
    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public NewsletterCreateService(
            NewsletterMapper newsletterMapper,
            Environment environment,
            Clock clock
    ) {
        this.newsletterMapper = newsletterMapper;
        this.environment = environment;
        this.clock = clock;
    }

    @Transactional
    public Newsletter create(NewsletterCreateRequest request) {
        validate(request);

        LocalDateTime now = LocalDateTime.now(clock);
        Newsletter newsletter = Newsletter.review(
                request.weekStartDate(),
                request.headline(),
                toJson(request.financialWordsJson()),
                toJson(request.issuesJson()),
                toJson(request.statsJson()),
                NewsletterGenerationType.AI,
                resolveAiCreatedBy(),
                now
        );

        if (newsletterMapper.insert(newsletter) != 1 || newsletter.getNewsletterId() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        return newsletter;
    }

    private void validate(NewsletterCreateRequest request) {
        if (request.weekStartDate() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "week_start_date는 필수입니다.");
        }
        if (request.weekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "week_start_date는 월요일이어야 합니다.");
        }
        if (isBlank(request.headline())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "headline은 비어 있을 수 없습니다.");
        }
        validateFinancialWords(request.financialWordsJson());
        validateIssues(request.issuesJson());
        validateStats(request.statsJson());
    }

    private void validateFinancialWords(List<FinancialWordRequest> financialWords) {
        requireExactSize(financialWords, "financial_words_json");
        for (FinancialWordRequest word : financialWords) {
            if (isBlank(word.term()) || isBlank(word.definition())) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "financial_words_json의 term, definition은 비어 있을 수 없습니다."
                );
            }
        }
    }

    private void validateIssues(List<NewsletterIssueRequest> issues) {
        requireExactSize(issues, "issues_json");
        for (NewsletterIssueRequest issue : issues) {
            if (isBlank(issue.title()) || isBlank(issue.summary()) || isBlank(issue.relatedTerm())) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "issues_json의 title, summary, related_term은 비어 있을 수 없습니다."
                );
            }
            if (issue.sources() == null || issue.sources().isEmpty()) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "issues_json 각 항목의 sources는 비어 있을 수 없습니다."
                );
            }
        }
    }

    private void validateStats(List<NewsletterStatRequest> stats) {
        requireExactSize(stats, "stats_json");
        for (NewsletterStatRequest stat : stats) {
            if (isBlank(stat.label()) || isBlank(stat.value())) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "stats_json의 label, value는 비어 있을 수 없습니다."
                );
            }
        }
    }

    private void requireExactSize(List<?> items, String fieldName) {
        if (items == null || items.size() != REQUIRED_SECTION_SIZE) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    fieldName + "은(는) 정확히 " + REQUIRED_SECTION_SIZE + "개여야 합니다."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long resolveAiCreatedBy() {
        String value = environment.getProperty(AI_CREATED_BY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    AI_CREATED_BY_PROPERTY + " 설정이 없어 뉴스레터를 저장할 수 없습니다."
            );
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    AI_CREATED_BY_PROPERTY + " 값이 올바른 사용자 ID가 아닙니다: " + value
            );
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("뉴스레터 JSON을 직렬화할 수 없습니다.", exception);
        }
    }
}
