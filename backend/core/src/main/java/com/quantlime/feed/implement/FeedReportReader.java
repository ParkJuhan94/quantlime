package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedReportTarget;
import com.quantlime.feed.repository.FeedReportRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 피드 신고 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class FeedReportReader {

    private final FeedReportRepository feedReportRepository;

    public boolean existsByReporter_IdAndTargetTypeAndTargetId(Long reporterId, FeedReportTarget targetType, Long targetId) {
        return feedReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(reporterId, targetType, targetId);
    }

    public long countByTargetTypeAndTargetId(FeedReportTarget targetType, Long targetId) {
        return feedReportRepository.countByTargetTypeAndTargetId(targetType, targetId);
    }

    public List<Object[]> countByTargetIds(FeedReportTarget targetType, Collection<Long> targetIds) {
        return feedReportRepository.countByTargetIds(targetType, targetIds);
    }
}
