package org.firstfolio.user.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.user.domain.PointBalanceSnapshot;
import org.firstfolio.user.domain.UserRole;
import org.firstfolio.user.service.PointBalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PointControllerTest {

    @Test
    void getsMyPointBalance() throws Exception {
        PointBalanceService service = mock(PointBalanceService.class);
        PointBalanceSnapshot snapshot = new PointBalanceSnapshot();
        snapshot.setPointBalance(4700);
        snapshot.setUpdatedAt(LocalDateTime.of(2026, 7, 29, 3, 30));
        when(service.get(101L)).thenReturn(snapshot);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PointController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create()))
                .build();

        mockMvc.perform(get("/api/points/balance")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER,
                                new AuthenticatedUser(101L, "firebase-1", "새싹투자자", UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.point_balance").value(4700))
                .andExpect(jsonPath("$.data.updated_at").value("2026-07-29T03:30:00Z"));
    }
}
