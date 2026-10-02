package com.quantlime.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("integration")
class UserControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("[로그인 사용자의 닉네임/이메일을 반환한다]")
    void getMe_returnsProfile() throws Exception {
        User user = userRepository.save(UserFixture.createUser());
        String token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("테스트유저"))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("[로그인 없이 호출하면 401]")
    void getMe_withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[토큰은 유효하지만 탈퇴 등으로 사용자가 없으면 404]")
    void getMe_deletedUser_returns404() throws Exception {
        String token = jwtTokenProvider.createAccessToken(999_999L, com.quantlime.user.domain.UserRole.USER);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }
}
