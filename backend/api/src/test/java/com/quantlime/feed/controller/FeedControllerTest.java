package com.quantlime.feed.controller;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class FeedControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(UserFixture.createUser());
        accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private long createPostAndGetId() throws Exception {
        String response = mockMvc.perform(post("/api/feed/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"국내주식토론","title":"오늘 장 어때요"}
                    """))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("[로그인한 사용자가 글을 작성하면 200과 작성한 글을 반환한다]")
    void createPost_authenticated_returns200() throws Exception {
        mockMvc.perform(post("/api/feed/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"국내주식토론","title":"오늘 장 어때요"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("오늘 장 어때요"))
            .andExpect(jsonPath("$.category").value("국내주식토론"));
    }

    @Test
    @DisplayName("[로그인하지 않으면 401을 반환한다]")
    void createPost_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/feed/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"국내주식토론","title":"오늘 장 어때요"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[존재하지 않는 주제로 작성하면 400을 반환한다]")
    void createPost_invalidCategory_returns400() throws Exception {
        mockMvc.perform(post("/api/feed/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"없는주제","title":"제목"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[글 목록 조회는 로그인 없이도 가능하고 방금 작성한 글이 보인다]")
    void getPosts_withoutAuth_returnsCreatedPost() throws Exception {
        // given
        mockMvc.perform(post("/api/feed/posts")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"category":"아무말대잔치","title":"점심 뭐 드세요"}
                """));

        // when & then
        mockMvc.perform(get("/api/feed/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("점심 뭐 드세요"));
    }

    @Test
    @DisplayName("[category로 필터링하면 해당 주제 글만 반환한다]")
    void getPosts_withCategory_filtersOnlyMatching() throws Exception {
        // given
        mockMvc.perform(post("/api/feed/posts")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"category":"미국주식이야기","title":"엔비디아 어때요"}
                """));

        // when & then
        mockMvc.perform(get("/api/feed/posts").param("category", "국내주식토론"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("[좋아요를 누르면 목록 조회 시 likeCount·likedByMe에 반영된다]")
    void likePost_thenGetPosts_reflectsLikeCountAndLikedByMe() throws Exception {
        // given
        long postId = createPostAndGetId();

        // when
        mockMvc.perform(post("/api/feed/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        // then
        mockMvc.perform(get("/api/feed/posts").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].likeCount").value(1))
            .andExpect(jsonPath("$.content[0].likedByMe").value(true));
    }

    @Test
    @DisplayName("[좋아요 취소하면 likeCount가 다시 0으로 돌아온다]")
    void unlikePost_afterLike_returnsLikeCountToZero() throws Exception {
        // given
        long postId = createPostAndGetId();
        mockMvc.perform(post("/api/feed/posts/" + postId + "/like")
            .header("Authorization", "Bearer " + accessToken));

        // when
        mockMvc.perform(delete("/api/feed/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        // then
        mockMvc.perform(get("/api/feed/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].likeCount").value(0))
            .andExpect(jsonPath("$.content[0].likedByMe").value(false));
    }

    @Test
    @DisplayName("[로그인 없이 좋아요를 누르면 401을 반환한다]")
    void likePost_unauthenticated_returns401() throws Exception {
        long postId = createPostAndGetId();

        mockMvc.perform(post("/api/feed/posts/" + postId + "/like"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[댓글을 작성하면 200과 작성한 댓글을 반환하고, 목록 조회로 확인된다]")
    void createComment_authenticated_returns200AndListsIt() throws Exception {
        // given
        long postId = createPostAndGetId();

        // when & then
        mockMvc.perform(post("/api/feed/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"좋은 글이네요"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("좋은 글이네요"));

        mockMvc.perform(get("/api/feed/posts/" + postId + "/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].content").value("좋은 글이네요"));

        mockMvc.perform(get("/api/feed/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].commentCount").value(1));
    }

    @Test
    @DisplayName("[로그인 없이 댓글을 작성하면 401을 반환한다]")
    void createComment_unauthenticated_returns401() throws Exception {
        long postId = createPostAndGetId();

        mockMvc.perform(post("/api/feed/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"내용"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[작성자 본인이 글을 수정하면 200과 수정된 내용을 반환한다]")
    void updatePost_owner_returns200AndUpdatedContent() throws Exception {
        // given
        long postId = createPostAndGetId();

        // when & then
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"미국주식이야기","title":"수정된 제목"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정된 제목"))
            .andExpect(jsonPath("$.category").value("미국주식이야기"))
            .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    @DisplayName("[다른 사용자의 글을 수정하려 하면 404를 반환한다]")
    void updatePost_notOwner_returns404() throws Exception {
        // given
        long postId = createPostAndGetId();
        User otherUser = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "other-provider-id"));
        String otherAccessToken = jwtTokenProvider.createAccessToken(otherUser.getId(), otherUser.getRole());

        // when & then
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                .header("Authorization", "Bearer " + otherAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"미국주식이야기","title":"몰래 수정"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[로그인 없이 글을 수정하면 401을 반환한다]")
    void updatePost_unauthenticated_returns401() throws Exception {
        long postId = createPostAndGetId();

        mockMvc.perform(patch("/api/feed/posts/" + postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"미국주식이야기","title":"제목"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[작성자 본인이 글을 삭제하면 204를 반환하고 목록에서 사라진다]")
    void deletePost_owner_returns204AndRemovesFromList() throws Exception {
        // given
        long postId = createPostAndGetId();

        // when
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        // then
        mockMvc.perform(get("/api/feed/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("[다른 사용자의 글을 삭제하려 하면 404를 반환하고 글이 그대로 남는다]")
    void deletePost_notOwner_returns404() throws Exception {
        // given
        long postId = createPostAndGetId();
        User otherUser = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "other-provider-id"));
        String otherAccessToken = jwtTokenProvider.createAccessToken(otherUser.getId(), otherUser.getRole());

        // when
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                .header("Authorization", "Bearer " + otherAccessToken))
            .andExpect(status().isNotFound());

        // then
        mockMvc.perform(get("/api/feed/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(postId));
    }

    @Test
    @DisplayName("[로그인 없이 글을 삭제하면 401을 반환한다]")
    void deletePost_unauthenticated_returns401() throws Exception {
        long postId = createPostAndGetId();

        mockMvc.perform(delete("/api/feed/posts/" + postId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[글 작성자 본인이 조회하면 mine=true, 다른 사용자가 조회하면 mine=false다]")
    void getPosts_mineReflectsViewer() throws Exception {
        // given
        createPostAndGetId();
        User otherUser = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "other-provider-id"));
        String otherAccessToken = jwtTokenProvider.createAccessToken(otherUser.getId(), otherUser.getRole());

        // when & then
        mockMvc.perform(get("/api/feed/posts").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.content[0].mine").value(true));
        mockMvc.perform(get("/api/feed/posts").header("Authorization", "Bearer " + otherAccessToken))
            .andExpect(jsonPath("$.content[0].mine").value(false));
        mockMvc.perform(get("/api/feed/posts"))
            .andExpect(jsonPath("$.content[0].mine").value(false));
    }
}
