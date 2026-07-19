package com.quantlab.watchlist.controller;

import com.quantlab.auth.resolver.LoginUser;
import com.quantlab.watchlist.domain.Watchlist;
import com.quantlab.watchlist.dto.mapper.WatchlistMapper;
import com.quantlab.watchlist.dto.request.AddWatchlistRequest;
import com.quantlab.watchlist.dto.request.MoveWatchlistRequest;
import com.quantlab.watchlist.dto.request.ReorderWatchlistRequest;
import com.quantlab.watchlist.dto.response.WatchlistResponse;
import com.quantlab.watchlist.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관심 종목 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    @Operation(summary = "관심 종목 목록 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<WatchlistResponse>> getWatchlist(@LoginUser Long userId) {
        List<WatchlistResponse> response = watchlistService.getWatchlist(userId).stream()
            .map(WatchlistMapper::toWatchlistResponse)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{stockCode}")
    @Operation(summary = "관심 종목 등록(등록과 동시에 그룹 지정 필수)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<WatchlistResponse> addWatchlist(
        @LoginUser Long userId, @PathVariable String stockCode,
        @Valid @RequestBody AddWatchlistRequest request) {
        Watchlist watchlist = watchlistService.addWatchlist(userId, stockCode, request.groupId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(WatchlistMapper.toWatchlistResponse(watchlist));
    }

    @DeleteMapping("/{stockCode}")
    @Operation(summary = "관심 종목 해제")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> removeWatchlist(
        @LoginUser Long userId, @PathVariable String stockCode) {
        watchlistService.removeWatchlist(userId, stockCode);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{stockCode}/group")
    @Operation(summary = "관심 종목을 다른 그룹으로 이동")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> moveWatchlistGroup(
        @LoginUser Long userId, @PathVariable String stockCode,
        @Valid @RequestBody MoveWatchlistRequest request) {
        watchlistService.moveToGroup(userId, stockCode, request.groupId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @Operation(summary = "관심 종목 순서 변경(같은 그룹 안에서의 순서)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> reorderWatchlist(
        @LoginUser Long userId, @Valid @RequestBody ReorderWatchlistRequest request) {
        watchlistService.reorderWatchlist(userId, request.watchlistIds());
        return ResponseEntity.noContent().build();
    }
}
