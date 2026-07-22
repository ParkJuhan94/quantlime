package com.quantlime.upload.controller;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class UploadControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(UserFixture.createUser());
        accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    @DisplayName("[로그인한 사용자가 이미지를 업로드하면 200과 /uploads/ 경로를 반환한다]")
    void uploadImage_authenticated_returns200WithPublicPath() throws Exception {
        MockMultipartFile file =
            new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/uploads/images")
                .file(file)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrl").value(startsWith("/uploads/")));
    }

    @Test
    @DisplayName("[로그인하지 않으면 401을 반환한다]")
    void uploadImage_unauthenticated_returns401() throws Exception {
        MockMultipartFile file =
            new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/uploads/images").file(file))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[이미지가 아닌 파일이면 400을 반환한다]")
    void uploadImage_invalidFileType_returns400() throws Exception {
        MockMultipartFile file =
            new MockMultipartFile("image", "malware.exe", "application/x-msdownload", new byte[]{1});

        mockMvc.perform(multipart("/api/uploads/images")
                .file(file)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isBadRequest());
    }
}
