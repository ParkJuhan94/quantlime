package com.quantlime.watchlist.implement;

import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 관심 종목 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다. */
@Component
@RequiredArgsConstructor
public class WatchlistAppender {

    private final WatchlistRepository watchlistRepository;

    public Watchlist save(Watchlist watchlist) {
        return watchlistRepository.save(watchlist);
    }

    public void delete(Watchlist watchlist) {
        watchlistRepository.delete(watchlist);
    }
}
