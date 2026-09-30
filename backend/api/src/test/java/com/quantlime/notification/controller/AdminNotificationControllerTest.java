package com.quantlime.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.notification.repository.NotificationRepository;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.domain.UserRole;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@Tag("integration")
class AdminNotificationControllerTest extends ApiTestSupport {

    private static final String BODY = "{\"title\":\"점검 안내\",\"content\":\"내일 02시 점검\",\"linkUrl\":\"/\"}";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("[관리자가 공지를 발송하면 202이고 전체 사용자에게 인앱 알림이 저장된다]")
    void broadcast_asAdmin_returns202AndSavesForAllUsers() throws Exception {
        // given
        User admin = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "admin"));
        userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "u1"));
        userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "u2"));
        String adminToken = jwtTokenProvider.createAccessToken(admin.getId(), UserRole.ADMIN);

        // when & then
        mockMvc.perform(post("/api/admin/notifications/broadcast")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted());

        assertThat(notificationRepository.findAll()).hasSize(3)
            .allSatisfy(n -> assertThat(n.getTitle()).isEqualTo("점검 안내"));
    }

    @Test
    @DisplayName("[일반 사용자 토큰이면 403, 알림은 저장되지 않는다]")
    void broadcast_asNormalUser_returns403() throws Exception {
        User user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "u"));
        String userToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isForbidden());

        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("[로그인 없이 호출하면 401 또는 403으로 거부된다]")
    void broadcast_withoutLogin_isRejected() throws Exception {
        mockMvc.perform(post("/api/admin/notifications/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    @Test
    @DisplayName("[제목이 비어 있으면 400]")
    void broadcast_blankTitle_returns400() throws Exception {
        User admin = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "admin"));
        String adminToken = jwtTokenProvider.createAccessToken(admin.getId(), UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"content\":\"내용\"}"))
            .andExpect(status().isBadRequest());
    }
}
