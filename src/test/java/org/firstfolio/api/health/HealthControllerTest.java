package org.firstfolio.api.health;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이 테스트는 원래 {@code ServletConfig}로 서블릿 컨텍스트를 통째로 띄웠는데, 컨트롤러가
 * 늘어날수록 그 컨트롤러들이 의존하는 서비스 빈(루트 컨텍스트 소속)까지 필요해져서 깨진다.
 * 헬스체크 동작만 확인하면 되므로 컨트롤러 단독 구성으로 바꿨다.
 */
class HealthControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new HealthController())
            .setMessageConverters(
                    new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
            )
            .build();

    @Test
    void returnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"status\":\"UP\"}"));
    }
}
