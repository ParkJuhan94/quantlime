package com.quantlime.market.repository;

import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.domain.InvestorTrading;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorTradingRepository extends JpaRepository<InvestorTrading, Long> {

    Optional<InvestorTrading> findByMarketCodeAndAggregationIntervalAndBaseDate(
        String marketCode, AggregationInterval aggregationInterval, LocalDate baseDate);

    // 조회 API(count 파라미터)가 최신순 N건만 필요로 해 Pageable로 제한한다 -
    // 데이터 규모 자체가 시장 2개 x 최근 100건 수준으로 작아 Slice까지는 과함.
    List<InvestorTrading> findByMarketCodeAndAggregationIntervalOrderByBaseDateDesc(
        String marketCode, AggregationInterval aggregationInterval, Pageable pageable);
}
