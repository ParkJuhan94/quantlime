package com.quantlab.price.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlab.price.domain.QOverseasDailyPrice;
import com.quantlab.price.dto.OverseasStockTradingValue;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OverseasDailyPriceQueryRepositoryImpl implements OverseasDailyPriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OverseasStockTradingValue> findTopByTradingValue(LocalDate since, int limit) {
        QOverseasDailyPrice overseasDailyPrice = QOverseasDailyPrice.overseasDailyPrice;

        return queryFactory
            .select(Projections.constructor(OverseasStockTradingValue.class,
                overseasDailyPrice.stockCode,
                overseasDailyPrice.closePrice.multiply(overseasDailyPrice.volume).sum()))
            .from(overseasDailyPrice)
            .where(overseasDailyPrice.tradeDate.goe(since))
            .groupBy(overseasDailyPrice.stockCode)
            .orderBy(overseasDailyPrice.closePrice.multiply(overseasDailyPrice.volume).sum().desc())
            .limit(limit)
            .fetch();
    }
}
