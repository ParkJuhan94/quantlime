package com.quantlime.videofeed.implement;

import static org.mockito.Mockito.inOrder;

import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
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
class VideoRemoverTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private VideoTickerRepository videoTickerRepository;

    @InjectMocks
    private VideoRemover service;

    @Test
    @DisplayName("[자식(티커→요약→자막)을 먼저 지우고 마지막에 영상을 지운다 - FK 위반 방지 순서]")
    void deleteBatch_deletesChildrenBeforeVideos() {
        // when
        service.deleteBatch(List.of(1L, 2L));

        // then
        InOrder order = inOrder(videoTickerRepository, summaryRepository, transcriptRepository, videoRepository);
        order.verify(videoTickerRepository).deleteByVideo_IdIn(List.of(1L, 2L));
        order.verify(summaryRepository).deleteByVideo_IdIn(List.of(1L, 2L));
        order.verify(transcriptRepository).deleteByVideo_IdIn(List.of(1L, 2L));
        order.verify(videoRepository).deleteAllByIdInBatch(List.of(1L, 2L));
    }
}
