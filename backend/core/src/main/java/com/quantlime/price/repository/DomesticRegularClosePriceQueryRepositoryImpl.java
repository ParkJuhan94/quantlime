package com.quantlime.price.repository;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.domain.QDomesticRegularClosePrice;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DomesticRegularClosePriceQueryRepositoryImpl implements DomesticRegularClosePriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<DomesticRegularClosePrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date) {
        QDomesticRegularClosePrice regularClose = QDomesticRegularClosePrice.domesticRegularClosePrice;
        QDomesticRegularClosePrice latest = new QDomesticRegularClosePrice("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(regularClose)
            .where(
                regularClose.stockCode.in(stockCodes),
                regularClose.tradeDate.lt(date),
                regularClose.tradeDate.eq(latestTradeDateSubquery(latest, regularClose, date))
            )
            .fetch();
    }

    private JPQLQuery<LocalDate> latestTradeDateSubquery(
        QDomesticRegularClosePrice latest, QDomesticRegularClosePrice regularClose, LocalDate date) {
        return JPAExpressions
            .select(latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.eq(regularClose.stockCode), latest.tradeDate.lt(date));
    }
}
