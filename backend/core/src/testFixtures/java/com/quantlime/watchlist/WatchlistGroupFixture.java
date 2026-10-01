package com.quantlime.watchlist;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.user.domain.User;
import com.quantlime.watchlist.domain.WatchlistGroup;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class WatchlistGroupFixture {

    public static WatchlistGroup createWatchlistGroup(User user) {
        return WatchlistGroup.of(user, "테스트그룹", 0);
    }
}
