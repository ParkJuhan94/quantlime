package com.quantlime.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.repository.FcmTokenRepository;
import com.quantlime.notification.repository.NotificationRepository;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@Tag("integration")
class NotificationControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private User otherUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "notif-user"));
        otherUser = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "notif-other"));
        accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private Notification saveNotification(User owner, String title) {
        return notificationRepository.save(Notification.of(owner, NotificationType.SYSTEM, title, "내용", "/"));
    }

    @Test
    @DisplayName("[로그인 없이 알림 API를 호출하면 401]")
    void withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[FCM 토큰을 등록하면 201이고 저장된다]")
    void registerFcmToken_returns201() throws Exception {
        mockMvc.perform(post("/api/notifications/fcm-tokens")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok-1\",\"deviceInfo\":\"Chrome\"}"))
            .andExpect(status().isCreated());

        assertThat(fcmTokenRepository.findByToken("tok-1")).isPresent();
    }

    @Test
    @DisplayName("[토큰이 비어 있으면 400]")
    void registerFcmToken_blank_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications/fcm-tokens")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[같은 토큰을 다른 계정이 재등록하면 소유자가 교체된다(같은 브라우저 계정 전환)]")
    void registerFcmToken_sameTokenOtherAccount_reassignsOwner() throws Exception {
        String otherToken = jwtTokenProvider.createAccessToken(otherUser.getId(), otherUser.getRole());
        for (String bearer : new String[] {accessToken, otherToken}) {
            mockMvc.perform(post("/api/notifications/fcm-tokens")
                    .header("Authorization", "Bearer " + bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"shared-tok\"}"))
                .andExpect(status().isCreated());
        }

        assertThat(fcmTokenRepository.findAllByUser_Id(user.getId())).isEmpty();
        assertThat(fcmTokenRepository.findAllByUser_Id(otherUser.getId())).hasSize(1);
    }

    @Test
    @DisplayName("[FCM 토큰 삭제는 204이고 실제로 지워진다]")
    void deleteFcmToken_returns204() throws Exception {
        mockMvc.perform(post("/api/notifications/fcm-tokens")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"tok-del\"}"));

        mockMvc.perform(delete("/api/notifications/fcm-tokens").param("token", "tok-del")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findByToken("tok-del")).isEmpty();
    }

    @Test
    @DisplayName("[알림 목록은 내 알림만 최근순으로 주고 hasNext를 계산한다]")
    void getNotifications_onlyMine_pagedLatestFirst() throws Exception {
        saveNotification(user, "첫번째");
        saveNotification(user, "두번째");
        saveNotification(otherUser, "남의 알림");

        mockMvc.perform(get("/api/notifications").param("size", "1")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("두번째"))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("[안읽은 개수와 단건/전체 읽음 처리가 반영된다]")
    void unreadCount_andMarkRead() throws Exception {
        Notification first = saveNotification(user, "a");
        saveNotification(user, "b");

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(patch("/api/notifications/{id}/read", first.getId())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("[남의 알림을 읽음 처리하면 404 - 존재 여부를 응답으로 추론할 수 없다]")
    void markAsRead_otherUsersNotification_returns404() throws Exception {
        Notification others = saveNotification(otherUser, "남의 알림");

        mockMvc.perform(patch("/api/notifications/{id}/read", others.getId())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound());
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().isRead()).isFalse();
    }
}
