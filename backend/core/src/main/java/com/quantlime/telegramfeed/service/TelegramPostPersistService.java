package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.dto.CollectedTelegramPost;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 쓰기만 담당(외부 I/O 없음) - VideoPersistService와 동일 원칙. 같은
 * 글이 두 번 수집돼도 external_post_id UNIQUE + 저장 전 존재 확인으로
 * 멱등하게 방어한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPostPersistService {

    private final TelegramPostRepository telegramPostRepository;

    @Transactional
    public int upsertAll(Channel channel, List<CollectedTelegramPost> collectedPosts) {
        int insertedCount = 0;
        for (CollectedTelegramPost collected : collectedPosts) {
            if (telegramPostRepository.existsByExternalPostId(collected.externalPostId())) {
                continue;
            }
            LocalDateTime viewCountCheckedAt = collected.viewCount() != null ? LocalDateTime.now() : null;
            TelegramPost post = TelegramPost.of(
                channel,
                collected.externalPostId(),
                collected.messageId(),
                collected.content(),
                collected.publishedAt(),
                collected.viewCount(),
                viewCountCheckedAt,
                collected.hasMedia());
            telegramPostRepository.save(post);
            insertedCount++;
        }
        log.info("텔레그램 글 적재 완료: channel={}, 신규={}, 전체={}",
            channel.getName(), insertedCount, collectedPosts.size());
        return insertedCount;
    }
}
