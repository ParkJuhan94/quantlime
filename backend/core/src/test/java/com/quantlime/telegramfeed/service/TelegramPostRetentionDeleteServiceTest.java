package com.quantlime.telegramfeed.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramPostRetentionDeleteServiceTest {

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @Mock
    private TelegramDigestRepository telegramDigestRepository;

    @Mock
    private TelegramDigestTickerRepository telegramDigestTickerRepository;

    @InjectMocks
    private TelegramPostRetentionDeleteService service;

    @Test
    @DisplayName("[글 삭제는 글 테이블만 배치 삭제한다(자식 테이블 없음)]")
    void deletePostBatch_onlyDeletesPosts() {
        service.deletePostBatch(List.of(1L, 2L));

        verify(telegramPostRepository).deleteAllByIdInBatch(List.of(1L, 2L));
        verifyNoInteractions(telegramDigestRepository, telegramDigestTickerRepository);
    }

    @Test
    @DisplayName("[다이제스트 삭제는 티커를 먼저 지우고 다이제스트를 지운다 - FK 위반 방지 순서]")
    void deleteDigestBatch_deletesTickersBeforeDigests() {
        service.deleteDigestBatch(List.of(10L));

        InOrder order = inOrder(telegramDigestTickerRepository, telegramDigestRepository);
        order.verify(telegramDigestTickerRepository).deleteByTelegramDigest_IdIn(List.of(10L));
        order.verify(telegramDigestRepository).deleteAllByIdInBatch(List.of(10L));
        verifyNoInteractions(telegramPostRepository);
    }
}
