package com.quantlime.telegramfeed.implement;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TelegramPostRepository}의 텔레그램 글 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class TelegramPostReader {

    private final TelegramPostRepository telegramPostRepository;

    public Optional<Long> findMaxMessageIdByChannel(Channel channel) {
        return telegramPostRepository.findMaxMessageIdByChannel(channel);
    }

    public List<TelegramPost> findByChannelAndStatus(Channel channel, TelegramPostStatus status) {
        return telegramPostRepository.findByChannelAndStatus(channel, status);
    }

    public List<TelegramPost> findByChannelAndStatusAndPublishedAtBetween(Channel channel, TelegramPostStatus status, LocalDateTime start, LocalDateTime end) {
        return telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(channel, status, start, end);
    }

    public List<Object[]> findChannelIdAndPublishedAtForCounting(List<Long> channelIds, TelegramPostStatus status, LocalDateTime from, LocalDateTime to) {
        return telegramPostRepository.findChannelIdAndPublishedAtForCounting(channelIds, status, from, to);
    }

    public List<Long> findIdsByPublishedAtBefore(LocalDateTime cutoff) {
        return telegramPostRepository.findIdsByPublishedAtBefore(cutoff);
    }
}
