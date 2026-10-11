package com.quantlime.telegramfeed.implement;

import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * {@link TelegramDigestRepository}의 텔레그램 다이제스트 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class TelegramDigestReader {

    private final TelegramDigestRepository telegramDigestRepository;

    public Slice<TelegramDigest> findDigests(String tickerCode, Long channelId, LocalDate date, Pageable pageable) {
        return telegramDigestRepository.findDigests(tickerCode, channelId, date, pageable);
    }

    public Optional<TelegramDigest> findByIdWithChannel(Long telegramDigestId) {
        return telegramDigestRepository.findByIdWithChannel(telegramDigestId);
    }

    public List<Long> findExpiredIds(LocalDate cutoff) {
        return telegramDigestRepository.findIdsByDigestDateBefore(cutoff);
    }
}
