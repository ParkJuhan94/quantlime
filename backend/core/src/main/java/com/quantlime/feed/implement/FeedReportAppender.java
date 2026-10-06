package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedReport;
import com.quantlime.feed.domain.FeedReportTarget;
import com.quantlime.feed.repository.FeedReportRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 피드 신고 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다(파생 delete는 Repository의 @Transactional도 그대로 유효). */
@Component
@RequiredArgsConstructor
public class FeedReportAppender {

    private final FeedReportRepository feedReportRepository;

    public void deleteByTargetTypeAndTargetId(FeedReportTarget targetType, Long targetId) {
        feedReportRepository.deleteByTargetTypeAndTargetId(targetType, targetId);
    }

    public void deleteByTargetTypeAndTargetIdIn(FeedReportTarget targetType, Collection<Long> targetIds) {
        feedReportRepository.deleteByTargetTypeAndTargetIdIn(targetType, targetIds);
    }

    public FeedReport save(FeedReport report) {
        return feedReportRepository.save(report);
    }
}
