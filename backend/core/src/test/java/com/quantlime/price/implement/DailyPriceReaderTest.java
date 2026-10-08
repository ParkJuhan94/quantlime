package com.quantlime.price.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.DomesticStockTradingValue;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.price.dto.OverseasStockTradingValue;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 국내/해외 Repository 위임을 올바른 쪽(국내↔해외)에 연결했는지 확인한다 - 두 Repository의
 * 메서드 시그니처가 거의 같아서 잘못 연결해도 컴파일은 통과하기 때문이다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DailyPriceReaderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    @Mock
    private DomesticDailyPriceRepository domesticRepository;

    @Mock
    private OverseasDailyPriceRepository overseasRepository;

    @InjectMocks
    private DailyPriceReader reader;

    private final DomesticDailyPrice domesticRow =
        DomesticDailyPrice.of("005930", DATE, 100L, 110L, 90L, 105L, 1000L);
    private final OverseasDailyPrice overseasRow =
        OverseasDailyPrice.of("AAPL", DATE, 100.0, 110.0, 90.0, 105.0, 1000L);

    @Test
    @DisplayName("[거래대금 순위 조회는 국내/해외 각자의 Repository로 위임한다]")
    void tradingValueRanking_delegatesToEachMarket() {
        given(domesticRepository.findStockCodesOrderedByTradingValueDesc(DATE)).willReturn(List.of("005930"));
        given(overseasRepository.findStockCodesOrderedByTradingValueDesc(DATE)).willReturn(List.of("AAPL"));
        List<DomesticStockTradingValue> domesticTop = List.of(new DomesticStockTradingValue("005930", 10L));
        List<OverseasStockTradingValue> overseasTop = List.of(new OverseasStockTradingValue("AAPL", 10.0));
        given(domesticRepository.findTopByTradingValue(DATE, 5)).willReturn(domesticTop);
        given(overseasRepository.findTopByTradingValue(DATE, 5)).willReturn(overseasTop);

        assertThat(reader.findDomesticCodesByTradingValueDesc(DATE)).containsExactly("005930");
        assertThat(reader.findOverseasCodesByTradingValueDesc(DATE)).containsExactly("AAPL");
        assertThat(reader.findTopDomesticByTradingValue(DATE, 5)).isSameAs(domesticTop);
        assertThat(reader.findTopOverseasByTradingValue(DATE, 5)).isSameAs(overseasTop);
    }

    @Test
    @DisplayName("[최신 저장일 조회는 행이 있으면 거래일을, 없으면 빈 값을 돌려준다]")
    void latestTradeDate_mapsRowToDate() {
        given(domesticRepository.findTopByStockCodeOrderByTradeDateDesc("005930")).willReturn(Optional.of(domesticRow));
        given(overseasRepository.findTopByStockCodeOrderByTradeDateDesc("AAPL")).willReturn(Optional.of(overseasRow));
        given(domesticRepository.findTopByStockCodeOrderByTradeDateDesc("000000")).willReturn(Optional.empty());
        given(overseasRepository.findTopByStockCodeOrderByTradeDateDesc("ZZZZ")).willReturn(Optional.empty());

        assertThat(reader.findLatestDomesticTradeDate("005930")).contains(DATE);
        assertThat(reader.findLatestOverseasTradeDate("AAPL")).contains(DATE);
        assertThat(reader.findLatestDomesticTradeDate("000000")).isEmpty();
        assertThat(reader.findLatestOverseasTradeDate("ZZZZ")).isEmpty();
    }

    @Test
    @DisplayName("[국내 일봉 단건·존재·건수·최신 조회를 위임한다]")
    void domesticLookups_delegate() {
        given(domesticRepository.findByStockCodeAndTradeDate("005930", DATE)).willReturn(Optional.of(domesticRow));
        given(domesticRepository.existsByStockCodeAndTradeDate("005930", DATE)).willReturn(true);
        given(domesticRepository.countByStockCode("005930")).willReturn(7L);
        given(domesticRepository.findTopByStockCodeOrderByTradeDateDesc("005930")).willReturn(Optional.of(domesticRow));

        assertThat(reader.findDomestic("005930", DATE)).contains(domesticRow);
        assertThat(reader.existsDomestic("005930", DATE)).isTrue();
        assertThat(reader.countDomestic("005930")).isEqualTo(7L);
        assertThat(reader.findLatestDomestic("005930")).contains(domesticRow);
    }

    @Test
    @DisplayName("[국내 일봉 다건·기간·직전 조회와 유동성 스냅샷을 위임한다]")
    void domesticRangeLookups_delegate() {
        List<String> codes = List.of("005930");
        List<LiquiditySnapshot> snapshots = List.of(new LiquiditySnapshot("005930", 1.0, 0L));
        given(domesticRepository.findByStockCodeInAndTradeDate(codes, DATE)).willReturn(List.of(domesticRow));
        given(domesticRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc("005930", FROM, DATE))
            .willReturn(List.of(domesticRow));
        given(domesticRepository.findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(codes, FROM, DATE))
            .willReturn(List.of(domesticRow));
        given(domesticRepository.findLatestBeforeDate(codes, DATE)).willReturn(List.of(domesticRow));
        given(domesticRepository.findLiquiditySnapshot(FROM)).willReturn(snapshots);

        assertThat(reader.findDomesticByCodesAndDate(codes, DATE)).containsExactly(domesticRow);
        assertThat(reader.findDomesticBetween("005930", FROM, DATE)).containsExactly(domesticRow);
        assertThat(reader.findDomesticBetweenForCodes(codes, FROM, DATE)).containsExactly(domesticRow);
        assertThat(reader.findDomesticLatestBefore(codes, DATE)).containsExactly(domesticRow);
        assertThat(reader.findDomesticLiquiditySnapshot(FROM)).isSameAs(snapshots);
    }

    @Test
    @DisplayName("[해외 일봉 조회 전체를 해외 Repository로 위임한다]")
    void overseasLookups_delegate() {
        List<String> codes = List.of("AAPL");
        List<LiquiditySnapshot> snapshots = List.of(new LiquiditySnapshot("AAPL", 1.0, 0L));
        given(overseasRepository.findByStockCodeAndTradeDate("AAPL", DATE)).willReturn(Optional.of(overseasRow));
        given(overseasRepository.existsByStockCodeAndTradeDate("AAPL", DATE)).willReturn(true);
        given(overseasRepository.countByStockCode("AAPL")).willReturn(3L);
        given(overseasRepository.findTopByStockCodeOrderByTradeDateDesc("AAPL")).willReturn(Optional.of(overseasRow));
        given(overseasRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc("AAPL", FROM, DATE))
            .willReturn(List.of(overseasRow));
        given(overseasRepository.findLatestBeforeDate(codes, DATE)).willReturn(List.of(overseasRow));
        given(overseasRepository.findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(codes, FROM, DATE))
            .willReturn(List.of(overseasRow));
        given(overseasRepository.findLiquiditySnapshot(FROM)).willReturn(snapshots);

        assertThat(reader.findOverseas("AAPL", DATE)).contains(overseasRow);
        assertThat(reader.existsOverseas("AAPL", DATE)).isTrue();
        assertThat(reader.countOverseas("AAPL")).isEqualTo(3L);
        assertThat(reader.findLatestOverseas("AAPL")).contains(overseasRow);
        assertThat(reader.findOverseasBetween("AAPL", FROM, DATE)).containsExactly(overseasRow);
        assertThat(reader.findOverseasLatestBefore(codes, DATE)).containsExactly(overseasRow);
        assertThat(reader.findOverseasByCodesBetweenDesc(codes, FROM, DATE)).containsExactly(overseasRow);
        assertThat(reader.findOverseasLiquiditySnapshot(FROM)).isSameAs(snapshots);
    }
}
