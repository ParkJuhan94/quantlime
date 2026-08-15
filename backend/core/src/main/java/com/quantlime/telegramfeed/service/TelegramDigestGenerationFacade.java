package com.quantlime.telegramfeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.TelegramDigestGenerateResult;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 채널×오늘 단위로 그날 SELECTED된 텔레그램 글 전부를 하나로 합쳐 AI
 * 다이제스트를 생성한다(2026-08-15, 글 단위 SummaryCollectionFacade 대응물을
 * 대체). TelegramCollectionScheduler(수집, 1시간마다)와 스케줄을 분리해
 * 이 파사드는 하루 3회(08:30/13:30/20:30)만 실행된다 - 수집을 촘촘히 돌려도
 * 다이제스트 재생성(Gemini 호출)까지 그 빈도를 따라가면 유튜브와 공유하는
 * 무료 티어 일일 쿼터를 초과하기 때문(docs/ROADMAP.md "Phase 8 P7" 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramDigestGenerationFacade {

    private static final String LOCK_KEY = "lock:telegram-digest-generate";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final String CONTENT_SEPARATOR = "\n\n---\n\n";

    private final RedisLockService redisLockService;
    private final ChannelRepository channelRepository;
    private final TelegramPostRepository telegramPostRepository;
    private final PythonEngineClient pythonEngineClient;
    private final TelegramDigestPersistService telegramDigestPersistService;

    public Optional<List<TelegramDigestGenerateResult>> runAllExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::runAll);
    }

    // 락 없이 배치 로직만 실행 - 테스트에서 직접 호출. 스케줄러/관리자 엔드포인트는
    // 반드시 runAllExclusively()를 통해서만 호출할 것.
    public List<TelegramDigestGenerateResult> runAll() {
        List<Channel> channels = channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM);
        LocalDate today = LocalDate.now();
        List<TelegramDigestGenerateResult> results = new ArrayList<>();
        for (Channel channel : channels) {
            results.add(generateForChannel(channel, today));
        }
        return results;
    }

    private TelegramDigestGenerateResult generateForChannel(Channel channel, LocalDate date) {
        try {
            List<TelegramPost> posts = telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
                channel, TelegramPostStatus.SELECTED, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            if (posts.isEmpty()) {
                return TelegramDigestGenerateResult.skipped(channel.getName());
            }

            String combinedContent = posts.stream()
                .sorted(Comparator.comparing(TelegramPost::getPublishedAt))
                .map(TelegramPost::getContent)
                .collect(Collectors.joining(CONTENT_SEPARATOR));
            SummarizeApiResponse response = pythonEngineClient.summarize(
                new SummarizeApiRequest(null, channel.getName(), combinedContent, "telegram"));
            telegramDigestPersistService.persistResult(channel, date, response);
            return TelegramDigestGenerateResult.success(channel.getName(), posts.size());
        } catch (Exception e) {
            log.error("텔레그램 다이제스트 생성 실패: channel={}, date={}, reason={}",
                channel.getName(), date, e.getMessage(), e);
            return TelegramDigestGenerateResult.failed(channel.getName(), e.getMessage());
        }
    }
}
