package com.quantlime.videofeed.service;

import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.dto.TranscriptImportResult;
import com.quantlime.videofeed.dto.request.TranscriptImportRequest;
import com.quantlime.videofeed.repository.VideoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로컬에서 미리 수집한 자막을 운영 DB에 반영한다(2026-09 - youtube-transcript-api가
 * 운영 서버(AWS) IP를 차단해 직접 수집이 막힌 것에 대한 대응). 실제 저장은
 * {@link TranscriptPersistService}(정규 자막 수집 경로와 동일)에 위임해, 저장
 * 로직·이벤트 발행이 두 경로에서 갈라지지 않게 한다 - 이 서비스는 그 앞단에서
 * "가져와도 되는 영상인가"만 판단한다.
 *
 * <p>매 호출 전체(로컬 DB의 자막 전량)를 그대로 다시 보내도 안전하도록
 * 설계했다(2026-09-15, 사용자 요청 - 로컬이 간헐적으로만 켜지는 환경이라
 * "지난번에 뭘 보냈는지" 별도로 추적하지 않는 쪽이 빠뜨림 없이 더 안전함).
 * 이미 TRANSCRIBED/SUMMARIZED인 영상은 조용히 스킵되므로 중복 전송 자체가
 * 부작용이 없다 - 그래서 로컬 쪽에 "동기화 완료" 표시를 위한 별도 컬럼/상태를
 * 두지 않았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptImportService {

    private final VideoRepository videoRepository;
    private final TranscriptPersistService transcriptPersistService;

    public List<TranscriptImportResult> importAll(TranscriptImportRequest request) {
        return request.items().stream().map(this::importOne).toList();
    }

    private TranscriptImportResult importOne(TranscriptImportRequest.Item item) {
        Video video = videoRepository.findByExternalVideoId(item.externalVideoId()).orElse(null);
        if (video == null) {
            return TranscriptImportResult.notFound(item.externalVideoId());
        }
        VideoStatus status = video.getStatus();
        if (status == VideoStatus.TRANSCRIBED || status == VideoStatus.SUMMARIZED) {
            return TranscriptImportResult.alreadyDone(item.externalVideoId(), status.name());
        }
        if (status != VideoStatus.SELECTED && status != VideoStatus.FAILED) {
            return TranscriptImportResult.invalidStatus(item.externalVideoId(), status.name());
        }

        transcriptPersistService.persistResult(video.getId(),
            new TranscribeApiResponse(true, item.source(), item.lang(), item.content(), item.charCount(), null));
        log.info("자막 수동 임포트 완료: externalVideoId={}, videoId={}", item.externalVideoId(), video.getId());
        return TranscriptImportResult.imported(item.externalVideoId());
    }
}
