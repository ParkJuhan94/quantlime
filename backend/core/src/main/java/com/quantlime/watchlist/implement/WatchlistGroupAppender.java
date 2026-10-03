package com.quantlime.watchlist.implement;

import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 관심 종목 그룹 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다. */
@Component
@RequiredArgsConstructor
public class WatchlistGroupAppender {

    private final WatchlistGroupRepository watchlistGroupRepository;

    public WatchlistGroup save(WatchlistGroup group) {
        return watchlistGroupRepository.save(group);
    }

    public void delete(WatchlistGroup group) {
        watchlistGroupRepository.delete(group);
    }
}
