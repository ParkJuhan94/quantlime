package com.quantlime.watchlist.controller;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.watchlist.WatchlistGroupFixture;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class WatchlistControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private WatchlistGroupRepository watchlistGroupRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // 관심종목 등록은 별도 스레드에서 이력 백필(TossApiClient.getDailyCandles)을
    // 트리거한다. 실제 빈을 그대로 두면 이 통합테스트가 매번 진짜 토스증권
    // API를 호출하는데, 토스 토큰은 1개만 유효해(CLAUDE.md §4) CI가 재발급받는
    // 순간 로컬/운영에서 쓰던 토큰이 즉시 무효화된다 - 반드시 mock으로 격리.
    @MockBean
    private TossApiClient tossApiClient;

    private Stock stock;
    private String accessToken;
    private Long groupId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(UserFixture.createUser());
        stock = stockRepository.save(StockFixture.createStock());
        WatchlistGroup group = watchlistGroupRepository.save(WatchlistGroupFixture.createWatchlistGroup(user));
        groupId = group.getId();
        accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());

        // 빈 캔들 목록을 반환시켜 백필이 즉시 정상 종료되게 한다(캔들 매핑
        // 자체는 이 테스트의 관심사가 아님 - DailyPriceServiceTest에서 별도 검증).
        given(tossApiClient.getDailyCandles(anyString(), anyInt(), nullable(String.class)))
            .willReturn(new TossCandleResponse(
                new TossCandleResponse.TossCandlePageResult(List.of(), null)));
    }

    @Test
    @DisplayName("[관심 종목을 등록하면 201을 반환한다]")
    void addWatchlist_success_returns201() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":" + groupId + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stockCode").value(stock.getStockCode()))
            .andExpect(jsonPath("$.groupId").value(groupId));
    }

    @Test
    @DisplayName("[그룹 없이 등록하면 400을 반환한다]")
    void addWatchlist_withoutGroup_returns400() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[이미 등록된 종목을 다시 등록하면 400을 반환한다]")
    void addWatchlist_duplicate_returns400() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":" + groupId + "}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":" + groupId + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WL_000"));
    }

    @Test
    @DisplayName("[관심 종목 목록을 조회한다]")
    void getWatchlist_returnsRegisteredStocks() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":" + groupId + "}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/watchlist")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value(stock.getStockCode()));
    }

    @Test
    @DisplayName("[관심 종목을 해제하면 204를 반환한다]")
    void removeWatchlist_success_returns204() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":" + groupId + "}"))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[등록되지 않은 종목을 해제하면 404를 반환한다]")
    void removeWatchlist_notRegistered_returns404() throws Exception {
        mockMvc.perform(delete("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[토큰 없이 관심 종목 목록을 조회하면 401을 반환한다]")
    void getWatchlist_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/watchlist"))
            .andExpect(status().isUnauthorized());
    }
}
