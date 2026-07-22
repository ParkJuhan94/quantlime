package com.quantlime.watchlist.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.exception.WatchlistErrorCode;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistGroupService {

    // "미분류" 폐지 후 레거시 데이터 정리·그룹 삭제 시 소속 종목의 대피처로
    // 쓰는 기본 그룹 이름. 사용자가 직접 만든 그룹과 이름이 겹칠 수 있어
    // find-or-create는 항상 이 이름으로 먼저 조회한다.
    private static final String DEFAULT_GROUP_NAME = "기본";

    private final UserService userService;
    private final WatchlistGroupRepository watchlistGroupRepository;
    private final WatchlistRepository watchlistRepository;

    @Transactional(readOnly = true)
    public List<WatchlistGroup> getGroups(Long userId) {
        return watchlistGroupRepository.findAllByUser_IdOrderBySortOrderAsc(userId);
    }

    @Transactional
    public WatchlistGroup createGroup(Long userId, String name) {
        User user = userService.getById(userId);
        int nextSortOrder = (int) watchlistGroupRepository.countByUser_Id(userId);
        WatchlistGroup group = watchlistGroupRepository.save(WatchlistGroup.of(user, name, nextSortOrder));
        log.info("관심 그룹 생성 완료: userId={}, groupId={}, name={}", userId, group.getId(), name);
        return group;
    }

    @Transactional
    public WatchlistGroup renameGroup(Long userId, Long groupId, String name) {
        WatchlistGroup group = getOwnedGroup(userId, groupId);
        group.rename(name);
        return group;
    }

    @Transactional
    public void deleteGroup(Long userId, Long groupId) {
        WatchlistGroup group = getOwnedGroup(userId, groupId);
        // 그룹에 속한 관심 종목 자체는 삭제하지 않고 기본 그룹으로 옮긴다
        // ("미분류" 폐지 이후로는 어떤 경우에도 그룹 없는 상태를 만들지 않음).
        List<Watchlist> members = watchlistRepository.findAllByUser_IdAndGroup_Id(userId, groupId);
        if (!members.isEmpty()) {
            WatchlistGroup fallback = resolveFallbackGroup(userId, group);
            members.forEach(member -> member.assignToGroup(fallback));
        }
        watchlistGroupRepository.delete(group);
        log.info("관심 그룹 삭제 완료: userId={}, groupId={}, 기본 그룹으로 이동된 종목 수={}",
            userId, groupId, members.size());
    }

    /**
     * 삭제 대상 그룹이 마침 기본 그룹 자신인 특수 케이스를 처리한다 - 그
     * 경우 같은 그룹을 대피처로 재사용할 수 없으므로 새 기본 그룹을 만든다
     * (삭제 대상은 어차피 이 메서드 직후 삭제되므로 최종적으로 기본
     * 그룹은 정확히 하나만 남는다).
     */
    private WatchlistGroup resolveFallbackGroup(Long userId, WatchlistGroup groupBeingDeleted) {
        WatchlistGroup fallback = findOrCreateDefaultGroup(userId);
        if (!fallback.getId().equals(groupBeingDeleted.getId())) {
            return fallback;
        }
        User user = userService.getById(userId);
        int nextSortOrder = (int) watchlistGroupRepository.countByUser_Id(userId);
        return watchlistGroupRepository.save(WatchlistGroup.of(user, DEFAULT_GROUP_NAME, nextSortOrder));
    }

    /**
     * 사용자의 "기본" 그룹을 찾아 반환하고, 없으면 새로 만든다. 등록
     * 시점에 그룹 선택을 깜빡하고 넘어간 프론트 결함이나(있어선 안 되지만
     * 방어적으로), 그룹 삭제로 갈 곳을 잃은 종목의 대피처로 쓰인다.
     */
    @Transactional
    public WatchlistGroup findOrCreateDefaultGroup(Long userId) {
        return watchlistGroupRepository.findByUser_IdAndName(userId, DEFAULT_GROUP_NAME)
            .orElseGet(() -> {
                User user = userService.getById(userId);
                int nextSortOrder = (int) watchlistGroupRepository.countByUser_Id(userId);
                WatchlistGroup created = watchlistGroupRepository.save(
                    WatchlistGroup.of(user, DEFAULT_GROUP_NAME, nextSortOrder));
                log.info("기본 그룹 자동 생성: userId={}, groupId={}", userId, created.getId());
                return created;
            });
    }

    @Transactional
    public void reorderGroups(Long userId, List<Long> groupIds) {
        List<WatchlistGroup> groups = watchlistGroupRepository.findAllByUser_IdOrderBySortOrderAsc(userId);
        Map<Long, WatchlistGroup> groupById = new HashMap<>();
        groups.forEach(group -> groupById.put(group.getId(), group));

        for (int i = 0; i < groupIds.size(); i++) {
            WatchlistGroup group = groupById.get(groupIds.get(i));
            if (group != null) {
                group.updateSortOrder(i);
            }
        }
    }

    // WatchlistService(등록/이동 시 그룹 소유권 검증)에서도 재사용한다.
    public WatchlistGroup getOwnedGroup(Long userId, Long groupId) {
        return watchlistGroupRepository.findByIdAndUser_Id(groupId, userId)
            .orElseThrow(() -> new NotFoundException(WatchlistErrorCode.NOT_FOUND_WATCHLIST_GROUP));
    }
}
