package com.quantlime.watchlist.controller;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.dto.mapper.WatchlistMapper;
import com.quantlime.watchlist.dto.request.CreateWatchlistGroupRequest;
import com.quantlime.watchlist.dto.request.ReorderWatchlistGroupsRequest;
import com.quantlime.watchlist.dto.request.UpdateWatchlistGroupRequest;
import com.quantlime.watchlist.dto.response.WatchlistGroupResponse;
import com.quantlime.watchlist.service.WatchlistGroupService;
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

@Tag(name = "관심 그룹 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/watchlist/groups")
public class WatchlistGroupController {

    private final WatchlistGroupService watchlistGroupService;

    @GetMapping
    @Operation(summary = "관심 그룹 목록 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<WatchlistGroupResponse>> getGroups(@LoginUser Long userId) {
        List<WatchlistGroupResponse> response = watchlistGroupService.getGroups(userId).stream()
            .map(WatchlistMapper::toWatchlistGroupResponse)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "관심 그룹 생성")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<WatchlistGroupResponse> createGroup(
        @LoginUser Long userId, @Valid @RequestBody CreateWatchlistGroupRequest request) {
        WatchlistGroup group = watchlistGroupService.createGroup(userId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(WatchlistMapper.toWatchlistGroupResponse(group));
    }

    @PatchMapping("/{groupId}")
    @Operation(summary = "관심 그룹 이름 변경")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<WatchlistGroupResponse> renameGroup(
        @LoginUser Long userId, @PathVariable Long groupId,
        @Valid @RequestBody UpdateWatchlistGroupRequest request) {
        WatchlistGroup group = watchlistGroupService.renameGroup(userId, groupId, request.name());
        return ResponseEntity.ok(WatchlistMapper.toWatchlistGroupResponse(group));
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "관심 그룹 삭제(소속 종목은 기본 그룹으로 이동)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> deleteGroup(@LoginUser Long userId, @PathVariable Long groupId) {
        watchlistGroupService.deleteGroup(userId, groupId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @Operation(summary = "관심 그룹 순서 변경")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> reorderGroups(
        @LoginUser Long userId, @Valid @RequestBody ReorderWatchlistGroupsRequest request) {
        watchlistGroupService.reorderGroups(userId, request.groupIds());
        return ResponseEntity.noContent().build();
    }
}
