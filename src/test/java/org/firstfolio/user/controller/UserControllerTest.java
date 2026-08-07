package org.firstfolio.user.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.user.domain.User;
import org.firstfolio.user.domain.UserRole;
import org.firstfolio.user.dto.request.UserProfilePatchRequest;
import org.firstfolio.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserProfileService service;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        service = mock(UserProfileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create()))
                .build();
        user = User.signup("firebase-1", "user@example.com", "새싹투자자",
                LocalDateTime.of(2026, 8, 1, 0, 0));
        user.setUserId(101L);
        user.setPointBalance(4200);
    }

    @Test
    void getsMyProfile() throws Exception {
        when(service.get(101L)).thenReturn(user);
        mockMvc.perform(get("/api/users/me").requestAttr(
                        AuthenticationRequestAttributes.CURRENT_USER,
                        new AuthenticatedUser(101L, "firebase-1", "새싹투자자", UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(101))
                .andExpect(jsonPath("$.data.point_balance").value(4200));
    }

    @Test
    void patchesMyProfile() throws Exception {
        user.setNickname("채권꿈나무");
        user.setUpdatedAt(LocalDateTime.of(2026, 8, 6, 3, 0));
        when(service.patch(eq(101L), any(UserProfilePatchRequest.class))).thenReturn(user);
        mockMvc.perform(patch("/api/users/me")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER,
                                new AuthenticatedUser(101L, "firebase-1", "새싹투자자", UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"채권꿈나무\",\"newsletter_opt_in\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("채권꿈나무"));
    }
}
