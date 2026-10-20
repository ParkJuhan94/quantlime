package com.quantlime.notice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.notice.domain.Notice;
import com.quantlime.notice.repository.NoticeRepository;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.repository.NotificationRepository;
import com.quantlime.support.MockedServicesApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.domain.UserRole;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@Tag("integration")
class NoticeControllerTest extends MockedServicesApiTestSupport {

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User admin;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "notice-admin"));
        User normal = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "notice-user"));
        adminToken = jwtTokenProvider.createAccessToken(admin.getId(), UserRole.ADMIN);
        userToken = jwtTokenProvider.createAccessToken(normal.getId(), normal.getRole());
    }

    private String body(String title, String content, boolean sendNotification) {
        return "{\"title\":\"" + title + "\",\"content\":\"" + content + "\",\"sendNotification\":" + sendNotification
            + "}";
    }

    @Test
    @DisplayName("[로그인 없이 공지 목록(최신순, 본문은 미리보기)과 상세를 볼 수 있다]")
    void publicRead_withoutLogin() throws Exception {
        noticeRepository.save(Notice.of("첫 공지", "첫 번째 공지 본문"));
        Notice second = noticeRepository.save(Notice.of("두 번째 공지", "여러 줄\n본문\n입니다"));

        mockMvc.perform(get("/api/notices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].title").value("두 번째 공지"))
            .andExpect(jsonPath("$.content[0].preview").value("여러 줄 본문 입니다"));

        mockMvc.perform(get("/api/notices/" + second.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("두 번째 공지"))
            .andExpect(jsonPath("$.content").value("여러 줄\n본문\n입니다"));
    }

    @Test
    @DisplayName("[없는 공지는 404(NC_000)]")
    void getNotice_notFound() throws Exception {
        mockMvc.perform(get("/api/notices/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NC_000"));
    }

    @Test
    @DisplayName("[관리자가 알림 없이 등록하면 201이고 알림은 만들어지지 않는다]")
    void create_withoutNotification() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("점검 안내", "내일 02시 점검", false)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.notificationRequested").value(false))
            .andExpect(jsonPath("$.notificationSent").value(false));

        assertThat(noticeRepository.findAll()).hasSize(1);
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("[알림도 보내기를 켜면 전체 사용자에게 /notices/{id} 링크의 공지 알림이 저장된다]")
    void create_withNotification_savesLinkedNotificationForAllUsers() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("점검 안내", "내일 02시 점검", true)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.notificationSent").value(true));

        Long noticeId = noticeRepository.findAll().get(0).getId();
        assertThat(notificationRepository.findAll()).hasSize(2)
            .allSatisfy(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.ADMIN_NOTICE);
                assertThat(n.getTitle()).isEqualTo("점검 안내");
                assertThat(n.getLinkUrl()).isEqualTo("/notices/" + noticeId);
            });
    }

    @Test
    @DisplayName("[일반 사용자는 등록·수정·삭제할 수 없고(403) 아무것도 바뀌지 않는다]")
    void adminApis_asNormalUser_forbidden() throws Exception {
        Notice notice = noticeRepository.save(Notice.of("공지", "본문"));

        mockMvc.perform(post("/api/admin/notices").header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("t", "c", false)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/notices/" + notice.getId()).header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"content\":\"y\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/notices/" + notice.getId()).header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());

        assertThat(noticeRepository.findAll()).hasSize(1);
        assertThat(noticeRepository.findAll().get(0).getTitle()).isEqualTo("공지");
    }

    @Test
    @DisplayName("[관리자가 수정하면 제목·본문이 바뀌고 알림은 만들어지지 않으며, 삭제하면 상세가 404가 된다]")
    void update_thenDelete() throws Exception {
        Notice notice = noticeRepository.save(Notice.of("공지", "본문"));

        mockMvc.perform(put("/api/admin/notices/" + notice.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정된 공지\",\"content\":\"수정된 본문\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정된 공지"));
        assertThat(notificationRepository.findAll()).isEmpty();

        mockMvc.perform(delete("/api/admin/notices/" + notice.getId()).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notices/" + notice.getId())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[제목이 비었거나 본문이 5000자를 넘으면 400]")
    void create_validation() throws Exception {
        mockMvc.perform(post("/api/admin/notices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("", "본문", false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/notices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("제목", "가".repeat(5001), false)))
            .andExpect(status().isBadRequest());

        assertThat(noticeRepository.findAll()).isEmpty();
    }
}
