package com.quantlime.watchlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@Tag("integration")
class WatchlistGroupControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WatchlistGroupRepository watchlistGroupRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private User other;
    private String auth;

    @BeforeEach
    void setUp() {
        user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "wg-user"));
        other = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "wg-other"));
        auth = "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private WatchlistGroup saveGroup(User owner, String name, int sortOrder) {
        return watchlistGroupRepository.save(WatchlistGroup.of(owner, name, sortOrder));
    }

    @Test
    @DisplayName("[로그인 없이 호출하면 401]")
    void withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/watchlist/groups")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[그룹 목록은 내 그룹만 sortOrder 순으로 반환한다]")
    void getGroups_onlyMine_sorted() throws Exception {
        saveGroup(user, "둘째", 1);
        saveGroup(user, "첫째", 0);
        saveGroup(other, "남의그룹", 0);

        mockMvc.perform(get("/api/watchlist/groups").header("Authorization", auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("첫째"))
            .andExpect(jsonPath("$[1].name").value("둘째"));
    }

    @Test
    @DisplayName("[그룹을 만들면 201이고 맨 뒤 sortOrder를 받는다]")
    void createGroup_returns201() throws Exception {
        saveGroup(user, "기존", 0);

        mockMvc.perform(post("/api/watchlist/groups").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"성장주\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("성장주"))
            .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    @DisplayName("[이름이 비어 있으면 400]")
    void createGroup_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/watchlist/groups").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[이름 변경은 내 그룹이면 200, 남의 그룹이면 404]")
    void renameGroup_ownerOk_otherNotFound() throws Exception {
        WatchlistGroup mine = saveGroup(user, "옛이름", 0);
        WatchlistGroup others = saveGroup(other, "남의그룹", 0);

        mockMvc.perform(patch("/api/watchlist/groups/{id}", mine.getId()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새이름\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("새이름"));
        mockMvc.perform(patch("/api/watchlist/groups/{id}", others.getId()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"탈취\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[삭제는 204이고, 남의 그룹은 404이며 지워지지 않는다]")
    void deleteGroup_ownerOk_otherNotFoundAndKept() throws Exception {
        WatchlistGroup mine = saveGroup(user, "삭제대상", 0);
        WatchlistGroup others = saveGroup(other, "남의그룹", 0);

        mockMvc.perform(delete("/api/watchlist/groups/{id}", mine.getId()).header("Authorization", auth))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/watchlist/groups/{id}", others.getId()).header("Authorization", auth))
            .andExpect(status().isNotFound());

        assertThat(watchlistGroupRepository.findById(mine.getId())).isEmpty();
        assertThat(watchlistGroupRepository.findById(others.getId())).isPresent();
    }

    @Test
    @DisplayName("[순서 변경은 204이고 전달한 ID 순서대로 반영된다]")
    void reorderGroups_appliesOrder() throws Exception {
        WatchlistGroup a = saveGroup(user, "A", 0);
        WatchlistGroup b = saveGroup(user, "B", 1);

        mockMvc.perform(put("/api/watchlist/groups/reorder").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupIds\":[" + b.getId() + "," + a.getId() + "]}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/watchlist/groups").header("Authorization", auth))
            .andExpect(jsonPath("$[0].name").value("B"))
            .andExpect(jsonPath("$[1].name").value("A"));
    }

    @Test
    @DisplayName("[순서 변경 목록이 비어 있으면 400]")
    void reorderGroups_empty_returns400() throws Exception {
        mockMvc.perform(put("/api/watchlist/groups/reorder").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupIds\":[]}"))
            .andExpect(status().isBadRequest());
    }
}
