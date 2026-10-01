package com.quantlime.feed.repository;

import com.quantlime.feed.domain.FeedReport;
import com.quantlime.feed.domain.FeedReportTarget;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FeedReportRepository extends JpaRepository<FeedReport, Long> {

    boolean existsByReporter_IdAndTargetTypeAndTargetId(Long reporterId, FeedReportTarget targetType, Long targetId);

    // (reporter, target) 유니크라 행 수가 곧 "서로 다른 신고자 수"다.
    long countByTargetTypeAndTargetId(FeedReportTarget targetType, Long targetId);

    // 관리자 검토 목록에서 대상별 신고 수를 한 번에 채운다(N+1 방지).
    @Query("select r.targetId, count(r) from FeedReport r where r.targetType = :targetType "
        + "and r.targetId in :targetIds group by r.targetId")
    List<Object[]> countByTargetIds(
        @Param("targetType") FeedReportTarget targetType, @Param("targetIds") Collection<Long> targetIds);

    // 복구하면 신고를 초기화한다 - 안 지우면 복구 직후 신고 1건만 더 와도 임계치를 넘어 바로 다시 숨겨진다.
    @Transactional
    void deleteByTargetTypeAndTargetId(FeedReportTarget targetType, Long targetId);

    @Transactional
    void deleteByTargetTypeAndTargetIdIn(FeedReportTarget targetType, Collection<Long> targetIds);
}
