package org.firstfolio.exception;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommonExceptionAdviceTest {

    private final MockMvc mockMvc = buildMockMvc();

    private static MockMvc buildMockMvc() {
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create());

        return MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(converter)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    @DisplayName("ApiException은 오류 코드에 대응하는 상태와 error 봉투로 응답한다")
    void mapsApiExceptionToDocumentedErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/trade-not-allowed"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TRADE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.message").value("보유수량을 초과했습니다."))
                .andExpect(jsonPath("$.error.request_id").value(startsWith("req-")));
    }

    @Test
    @DisplayName("오류 코드마다 문서에 적힌 HTTP 상태를 그대로 쓴다")
    void usesDocumentedHttpStatusPerErrorCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_PORTFOLIO_NOT_FOUND"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 메시지를 감추고 500으로 응답한다")
    void hidesInternalDetailsOnUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(not("DB 비밀번호가 틀렸습니다")))
                .andExpect(jsonPath("$.error.request_id").value(startsWith("req-")));
    }

    @Test
    @DisplayName("성공 응답은 data 봉투로 감싼다")
    void wrapsSuccessInDataEnvelope() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio_id").value(8001));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/trade-not-allowed")
        public void tradeNotAllowed() {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "보유수량을 초과했습니다.");
        }

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ApiException(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND);
        }

        @GetMapping("/test/boom")
        public void boom() {
            throw new IllegalStateException("DB 비밀번호가 틀렸습니다");
        }

        @GetMapping("/test/ok")
        public ApiResponse<Payload> ok() {
            return ApiResponse.of(new Payload(8001L));
        }
    }

    static class Payload {

        private final Long portfolioId;

        Payload(Long portfolioId) {
            this.portfolioId = portfolioId;
        }

        public Long getPortfolioId() {
            return portfolioId;
        }
    }
}
