package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

    boolean existsByExternalVideoId(String externalVideoId);

    List<Video> findByChannelAndStatus(Channel channel, VideoStatus status);

    // max_per_run(채널별 회당 선택 상한)을 "한 번의 수집 사이클" 기준이 아니라
    // "영상 발행일 기준 하루" 단위로 적용하기 위한 카운트 - VideoFilterService.
    // selectUpToMaxPerRun 참고. 로컬 개발처럼 수집이 매일 규칙적으로 안 돌아가는
    // 환경에서는 한 사이클에 여러 날짜의 백로그가 한꺼번에 후보로 잡힐 수 있는데,
    // 사이클 기준 상한을 그대로 적용하면 그 백로그 전체에서 딱 max_per_run개만
    // 남고 나머지는 영구 FILTERED_OUT돼버린다(2026-08-08 실제 발견 - 신규 채널
    // 추가 시 과거 업로드 74개 중 3개만 살아남는 문제).
    int countByChannelAndStatusAndPublishedAtBetween(
        Channel channel, VideoStatus status, LocalDateTime start, LocalDateTime end);

    List<Video> findByStatusAndPublishedAtBefore(VideoStatus status, LocalDateTime publishedAt);

    // SELECTED(retryCount=0, 최초 시도)와 FAILED(재시도 대상, retryCount<maxRetryCount)를
    // 한 쿼리로 함께 잡는다 - "자막 없음"처럼 영구적으로 실패하는 영상도 FAILED로
    // 기록되는 동일 상태를 공유해 재시도 상한(TranscriptCollectionFacade 참고) 안에서
    // 반복 시도되지만, 그 낭비가 최대 시도 횟수로 bounded돼 있어 별도의 "재시도
    // 불가" 사유 구분 필드를 추가하지 않았다.
    //
    // FAILED가 자막 조회 실패와 요약 생성 실패(P4) 양쪽에서 공유되는 상태라,
    // "Transcript가 아직 없는" 조건을 반드시 함께 걸어야 한다 - 이게 없으면
    // 요약 단계에서 실패한(이미 자막은 있는) 영상까지 이 쿼리에 걸려 자막을
    // 중복 조회하려다 uk_transcript 유니크 제약(video_id)에 걸려 깨진다.
    @Query("select v from Video v where v.status in :statuses and v.retryCount < :maxRetryCount "
        + "and not exists (select 1 from Transcript t where t.video = v) "
        + "order by v.publishedAt asc")
    Slice<Video> findTranscribeCandidates(
        @Param("statuses") List<VideoStatus> statuses, @Param("maxRetryCount") int maxRetryCount,
        Pageable pageable);

    // 자막(Transcript)은 이미 있고 요약(Summary)은 아직 없는 영상만 후보로 잡는다 -
    // 위 findTranscribeCandidates와 동일한 이유로, "이미 자막이 있다"는 존재
    // 조건이 없으면 아직 SELECTED 단계인 영상이나 자막 조회 실패로 FAILED된
    // 영상까지 섞여 들어와 자막 없이 요약을 시도하게 된다.
    //
    // join fetch로 channel을 미리 로딩한다 - SummaryCollectionFacade가 이 결과를
    // (findSummarizeCandidates 자체의 트랜잭션이 끝난) 트랜잭션 밖에서
    // video.getChannel().getName()으로 채널명을 읽는데, LAZY 연관관계를 지연
    // 로딩하려 하면 LazyInitializationException이 난다.
    @Query("select v from Video v join fetch v.channel "
        + "where v.status in :statuses and v.retryCount < :maxRetryCount "
        + "and exists (select 1 from Transcript t where t.video = v) "
        + "and not exists (select 1 from Summary s where s.video = v) "
        + "order by v.publishedAt asc")
    Slice<Video> findSummarizeCandidates(
        @Param("statuses") List<VideoStatus> statuses, @Param("maxRetryCount") int maxRetryCount,
        Pageable pageable);

    // P6(프론트 피드 노출) - 요약까지 끝난 영상만 최신순으로 공개 노출한다.
    // tickerCode/channelId/publishedFrom/publishedTo는 전부 선택 필터라
    // 널이면 그 조건 자체를 건너뛴다(JPQL의 ":param is null or ..." 패턴) -
    // 필터 조합마다 별도 쿼리 메서드로 만들지 않기 위함. join fetch로
    // channel을 미리 로딩(채널명/프로필사진 표시용, 위
    // findSummarizeCandidates와 동일한 LazyInitializationException 방지 이유).
    @Query("select v from Video v join fetch v.channel "
        + "where v.status = com.quantlime.videofeed.domain.VideoStatus.SUMMARIZED "
        + "and (:tickerCode is null or exists "
        + "  (select 1 from VideoTicker vt where vt.video = v and vt.tickerCode = :tickerCode)) "
        + "and (:channelId is null or v.channel.id = :channelId) "
        + "and (:publishedFrom is null or v.publishedAt >= :publishedFrom) "
        + "and (:publishedTo is null or v.publishedAt < :publishedTo) "
        + "order by v.publishedAt desc")
    Slice<Video> findSummarizedVideos(
        @Param("tickerCode") String tickerCode,
        @Param("channelId") Long channelId,
        @Param("publishedFrom") LocalDateTime publishedFrom,
        @Param("publishedTo") LocalDateTime publishedTo,
        Pageable pageable);

    @Query("select v from Video v join fetch v.channel "
        + "where v.id = :videoId and v.status = com.quantlime.videofeed.domain.VideoStatus.SUMMARIZED")
    Optional<Video> findSummarizedVideoById(@Param("videoId") Long videoId);

    // 보존 기간(VideoRetentionService.RETENTION_DAYS) 정리용 - 상태 무관하게
    // 발행일 기준으로만 대상을 잡는다(DISCOVERED/FILTERED_OUT처럼 애초에
    // 피드에 노출된 적 없는 영상도 함께 정리 대상).
    @Query("select v.id from Video v where v.publishedAt < :cutoff")
    List<Long> findIdsByPublishedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
