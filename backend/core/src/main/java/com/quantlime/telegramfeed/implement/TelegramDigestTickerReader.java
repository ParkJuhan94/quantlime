package com.quantlime.telegramfeed.implement;

import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TelegramDigestTickerRepository}의 텔레그램 다이제스트 종목 태그 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class TelegramDigestTickerReader {

    private final TelegramDigestTickerRepository telegramDigestTickerRepository;

    public List<TelegramDigestTicker> findByTelegramDigest(TelegramDigest telegramDigest) {
        return telegramDigestTickerRepository.findByTelegramDigest(telegramDigest);
    }

    public List<TelegramDigestTicker> findByTelegramDigest_IdIn(List<Long> telegramDigestIds) {
        return telegramDigestTickerRepository.findByTelegramDigest_IdIn(telegramDigestIds);
    }
}
