package com.quantlab.price.repository;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.domain.QDailyPrice;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyPriceQueryRepositoryImpl implements DailyPriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<DailyPrice> findLatestByStockCodesIn(List<String> stockCodes) {
        QDailyPrice dailyPrice = QDailyPrice.dailyPrice;
        QDailyPrice latest = new QDailyPrice("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(dailyPrice)
            .where(
                dailyPrice.stockCode.in(stockCodes),
                dailyPrice.tradeDate.eq(latestTradeDateSubquery(latest, dailyPrice))
            )
            .fetch();
    }

    private JPQLQuery<LocalDate> latestTradeDateSubquery(QDailyPrice latest, QDailyPrice dailyPrice) {
        return JPAExpressions
            .select(latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.eq(dailyPrice.stockCode));
    }
}
