package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

    boolean existsByExternalVideoId(String externalVideoId);

    List<Video> findByChannelAndStatus(Channel channel, VideoStatus status);

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
}
