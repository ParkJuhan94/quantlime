package com.quantlime.videofeed.service;

import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VideoRetentionService.deleteVideosOlderThanRetention()이
 * this::deleteVideosOlderThanRetention로 self-invocation되는 경로(runExclusively)를
 * 갖고 있어 그 메서드 자체엔 @Transactional을 붙일 수 없다(프록시를 안 타 무시됨) -
 * 실제 삭제 실행만 별도 빈으로 분리해 정상적으로 프록시를 타게 한다
 * (SummaryPersistService/TranscriptPersistService와 동일한 이유의 동일 패턴).
 *
 * <p>video_ticker/summary/transcript의 deleteByVideo_IdIn은 JpaRepository가
 * 기본 제공하는 메서드가 아니라 직접 선언한 파생 삭제 쿼리라, SimpleJpaRepository의
 * 클래스 레벨 @Transactional을 물려받지 못하고 호출부에 활성 트랜잭션이 없으면
 * TransactionRequiredException("No EntityManager with actual transaction
 * available")을 던진다(2026-08-08, StartupCatchUpRunner로 로컬 개발 DB에 처음
 * 실행되며 발견 - 기존 Testcontainers 테스트는 테스트 메서드 자체가 트랜잭션으로
 * 감싸여 있어 이 문제를 우연히 가리고 있었음. FeedService.deletePost()는
 * self-invocation 없이 컨트롤러에서 바로 호출돼 @Transactional이 정상 적용되므로
 * 이 문제가 없다). videoRepository.deleteAllByIdInBatch는 JpaRepository 내장
 * 메서드라 원래도 자체적으로 트랜잭셔널하지만, 네 삭제를 하나의 트랜잭션으로
 * 묶는 게 더 안전해 여기 함께 포함했다.
 *
 * <p>클래스/메서드를 public으로 둔다(2026-08-10). 처음엔 "package-private이면
 * AnnotationTransactionAttributeSource의 publicMethodsOnly 기본값(true) 때문에
 * @Transactional이 무시돼 위와 동일한 TransactionRequiredException이 재현될
 * 것"이라 추정했는데, VideoFeedTransactionProxyIntegrationTest(ApiTestSupport
 * 기반, 실제 프록시 체인 경유)로 직접 되돌려 검증해보니 이 앱에서는
 * package-private이어도 트랜잭션이 정상적으로 열려 재현되지 않았다 - 즉 이
 * 가시성 자체가 실제 버그를 일으키진 않는다(추정이 틀렸음을 테스트로 확인).
 * 그래도 public을 유지하는 건 TranscriptPersistService/SummaryPersistService와
 * 동일한 패턴을 지켜 향후 Spring 버전이 바뀌어 이 동작이 실제로 달라지더라도
 * 안전하게 하기 위한 방어적 선택이다.
 */
@Service
@RequiredArgsConstructor
public class VideoRetentionDeleteService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final SummaryRepository summaryRepository;
    private final VideoTickerRepository videoTickerRepository;

    @Transactional
    public void deleteBatch(List<Long> videoIds) {
        videoTickerRepository.deleteByVideo_IdIn(videoIds);
        summaryRepository.deleteByVideo_IdIn(videoIds);
        transcriptRepository.deleteByVideo_IdIn(videoIds);
        videoRepository.deleteAllByIdInBatch(videoIds);
    }
}
