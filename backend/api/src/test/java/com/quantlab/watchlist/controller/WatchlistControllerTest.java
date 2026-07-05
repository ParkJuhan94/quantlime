package com.quantlab.watchlist.controller;

import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.repository.StockRepository;
import com.quantlab.support.ApiTestSupport;
import com.quantlab.user.UserFixture;
import com.quantlab.user.domain.User;
import com.quantlab.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
    private JwtTokenProvider jwtTokenProvider;

    private Stock stock;
    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(UserFixture.createUser());
        stock = stockRepository.save(StockFixture.createStock());
        accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    @DisplayName("[관심 종목을 등록하면 201을 반환한다]")
    void addWatchlist_success_returns201() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stockCode").value(stock.getStockCode()));
    }

    @Test
    @DisplayName("[이미 등록된 종목을 다시 등록하면 400을 반환한다]")
    void addWatchlist_duplicate_returns400() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WL_000"));
    }

    @Test
    @DisplayName("[관심 종목 목록을 조회한다]")
    void getWatchlist_returnsRegisteredStocks() throws Exception {
        mockMvc.perform(post("/api/watchlist/{stockCode}", stock.getStockCode())
                .header("Authorization", "Bearer " + accessToken))
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
                .header("Authorization", "Bearer " + accessToken))
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
