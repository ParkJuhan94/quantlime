package com.quantlime.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.stock.StockFixture;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WatchlistGroupServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserService userService;

    @Mock
    private WatchlistGroupRepository watchlistGroupRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @InjectMocks
    private WatchlistGroupService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = UserFixture.createUser();
    }

    private WatchlistGroup group(long id, String name, int sortOrder) {
        WatchlistGroup group = WatchlistGroup.of(user, name, sortOrder);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    @Test
    @DisplayName("[새 그룹은 현재 그룹 수를 sortOrder로 받아 맨 뒤에 추가된다]")
    void createGroup_appendsAtEnd() {
        // given
        given(userService.getById(USER_ID)).willReturn(user);
        given(watchlistGroupRepository.countByUser_Id(USER_ID)).willReturn(3L);
        given(watchlistGroupRepository.save(any(WatchlistGroup.class))).willAnswer(i -> i.getArgument(0));

        // when
        WatchlistGroup created = service.createGroup(USER_ID, "성장주");

        // then
        assertThat(created.getName()).isEqualTo("성장주");
        assertThat(created.getSortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("[남의 그룹이거나 없는 그룹은 NotFoundException(이름 변경도 동일)]")
    void getOwnedGroup_notOwned_throws() {
        given(watchlistGroupRepository.findByIdAndUser_Id(5L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedGroup(USER_ID, 5L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.renameGroup(USER_ID, 5L, "새이름")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[그룹 이름을 바꾼다]")
    void renameGroup_renames() {
        WatchlistGroup group = group(5L, "옛이름", 0);
        given(watchlistGroupRepository.findByIdAndUser_Id(5L, USER_ID)).willReturn(Optional.of(group));

        assertThat(service.renameGroup(USER_ID, 5L, "새이름").getName()).isEqualTo("새이름");
    }

    @Test
    @DisplayName("[그룹을 삭제하면 소속 종목은 지우지 않고 기본 그룹으로 옮긴 뒤 그룹만 삭제한다]")
    void deleteGroup_movesMembersToDefaultGroup() {
        // given
        WatchlistGroup target = group(5L, "성장주", 1);
        WatchlistGroup fallback = group(9L, "기본", 0);
        Watchlist member = Watchlist.of(user, StockFixture.createStock(), target, 0);
        given(watchlistGroupRepository.findByIdAndUser_Id(5L, USER_ID)).willReturn(Optional.of(target));
        given(watchlistRepository.findAllByUser_IdAndGroup_Id(USER_ID, 5L)).willReturn(List.of(member));
        given(watchlistGroupRepository.findByUser_IdAndName(USER_ID, "기본")).willReturn(Optional.of(fallback));

        // when
        service.deleteGroup(USER_ID, 5L);

        // then
        assertThat(member.getGroup()).isSameAs(fallback);
        verify(watchlistGroupRepository).delete(target);
    }

    @Test
    @DisplayName("[소속 종목이 없으면 기본 그룹을 조회/생성하지 않고 그룹만 삭제한다]")
    void deleteGroup_noMembers_skipsFallback() {
        WatchlistGroup target = group(5L, "빈그룹", 1);
        given(watchlistGroupRepository.findByIdAndUser_Id(5L, USER_ID)).willReturn(Optional.of(target));
        given(watchlistRepository.findAllByUser_IdAndGroup_Id(USER_ID, 5L)).willReturn(List.of());

        service.deleteGroup(USER_ID, 5L);

        verify(watchlistGroupRepository, never()).findByUser_IdAndName(any(), any());
        verify(watchlistGroupRepository).delete(target);
    }

    @Test
    @DisplayName("[삭제 대상이 기본 그룹 자신이면 새 기본 그룹을 만들어 종목을 옮긴다 - 자기 자신을 대피처로 쓰지 않는다]")
    void deleteGroup_targetIsDefaultGroup_createsNewDefault() {
        // given
        WatchlistGroup defaultGroup = group(5L, "기본", 0);
        Watchlist member = Watchlist.of(user, StockFixture.createStock(), defaultGroup, 0);
        given(watchlistGroupRepository.findByIdAndUser_Id(5L, USER_ID)).willReturn(Optional.of(defaultGroup));
        given(watchlistRepository.findAllByUser_IdAndGroup_Id(USER_ID, 5L)).willReturn(List.of(member));
        given(watchlistGroupRepository.findByUser_IdAndName(USER_ID, "기본")).willReturn(Optional.of(defaultGroup));
        given(userService.getById(USER_ID)).willReturn(user);
        given(watchlistGroupRepository.countByUser_Id(USER_ID)).willReturn(1L);
        given(watchlistGroupRepository.save(any(WatchlistGroup.class))).willAnswer(i -> i.getArgument(0));

        // when
        service.deleteGroup(USER_ID, 5L);

        // then
        assertThat(member.getGroup()).isNotSameAs(defaultGroup);
        assertThat(member.getGroup().getName()).isEqualTo("기본");
        verify(watchlistGroupRepository).delete(defaultGroup);
    }

    @Test
    @DisplayName("[기본 그룹이 이미 있으면 재사용하고, 없으면 새로 만든다]")
    void findOrCreateDefaultGroup_reusesOrCreates() {
        // given
        WatchlistGroup existing = group(9L, "기본", 0);
        given(watchlistGroupRepository.findByUser_IdAndName(1L, "기본")).willReturn(Optional.of(existing));
        given(watchlistGroupRepository.findByUser_IdAndName(2L, "기본")).willReturn(Optional.empty());
        given(userService.getById(2L)).willReturn(user);
        given(watchlistGroupRepository.countByUser_Id(2L)).willReturn(4L);
        given(watchlistGroupRepository.save(any(WatchlistGroup.class))).willAnswer(i -> i.getArgument(0));

        // when & then
        assertThat(service.findOrCreateDefaultGroup(1L)).isSameAs(existing);
        WatchlistGroup created = service.findOrCreateDefaultGroup(2L);
        assertThat(created.getName()).isEqualTo("기본");
        assertThat(created.getSortOrder()).isEqualTo(4);
    }

    @Test
    @DisplayName("[순서 변경은 전달된 ID 순서대로 sortOrder를 다시 매기고, 내 그룹이 아닌 ID는 무시한다]")
    void reorderGroups_reassignsSortOrder_ignoringUnknownIds() {
        // given
        WatchlistGroup a = group(1L, "A", 0);
        WatchlistGroup b = group(2L, "B", 1);
        WatchlistGroup c = group(3L, "C", 2);
        given(watchlistGroupRepository.findAllByUser_IdOrderBySortOrderAsc(USER_ID)).willReturn(List.of(a, b, c));

        // when: C, (남의 그룹 99), A, B
        service.reorderGroups(USER_ID, List.of(3L, 99L, 1L, 2L));

        // then: 인덱스 기준으로 부여된다(무시된 항목도 자리는 차지)
        assertThat(c.getSortOrder()).isEqualTo(0);
        assertThat(a.getSortOrder()).isEqualTo(2);
        assertThat(b.getSortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("[그룹 목록은 sortOrder 오름차순 조회에 위임한다]")
    void getGroups_delegates() {
        List<WatchlistGroup> groups = List.of(group(1L, "A", 0));
        given(watchlistGroupRepository.findAllByUser_IdOrderBySortOrderAsc(USER_ID)).willReturn(groups);

        assertThat(service.getGroups(USER_ID)).isSameAs(groups);
    }
}
