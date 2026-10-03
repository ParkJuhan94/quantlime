package com.quantlime.backtest.implement;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.domain.BacktestSampleSplit;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.backtest.dto.mapper.BacktestMapper;
import com.quantlime.backtest.dto.mapper.BacktestRequestMapper;
import com.quantlime.backtest.dto.mapper.CrossSectionalBacktestMapper;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.stock.domain.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 퀀트 엔진(Python) 백테스트 호출을 감싸는 구현 레이어(Implementation) - 도메인 객체를 엔진
 * 요청으로 바꾸고, 호출하고, 응답을 {@link BacktestResult}/{@link CrossSectionalBacktestResult}
 * 같은 도메인 결과로 되돌리는 "계산 위임"을 이 컴포넌트가 맡는다. 서비스는 어떤 데이터로
 * 언제 돌릴지와 저장만 한다. 엔진 장애 처리(타임아웃·서킷)는 {@link PythonEngineClient} 책임이다.
 */
@Component
@RequiredArgsConstructor
public class BacktestEngineProcessor {

    private final PythonEngineClient pythonEngineClient;

    /** 종목 단건 백테스트의 엔진 결과 - 저장은 호출부가 한다. */
    public record StockBacktestOutput(
        List<BacktestResult> results,
        List<BacktestDailyScore> dailyScores,
        String scoreVersion,
        int sampleDays) {
    }

    public StockBacktestOutput runDomestic(
        String stockCode, List<DomesticDailyPrice> prices, List<BenchmarkIndex> benchmarkPrices, LocalDate end) {
        BacktestApiRequest request =
            BacktestRequestMapper.toBacktestApiRequest(stockCode, prices, benchmarkPrices);
        return toOutput(pythonEngineClient.runBacktest(request), end);
    }

    public StockBacktestOutput runOverseas(
        String stockCode, List<OverseasDailyPrice> prices, List<BenchmarkIndex> benchmarkPrices, LocalDate end) {
        BacktestApiRequest request =
            BacktestRequestMapper.toOverseasBacktestApiRequest(stockCode, prices, benchmarkPrices);
        return toOutput(pythonEngineClient.runBacktest(request), end);
    }

    /** 시장·축·horizon 한 조합의 횡단면 백테스트(FULL 구간) 결과. */
    public CrossSectionalBacktestResult runCrossSectional(
        MarketType market, String scoreVersion, Map<String, List<BacktestDailyScore>> dailyScoresByStock,
        List<BenchmarkIndex> benchmarkPrices, BacktestAxis axis, int horizonDays,
        boolean nullTest, int nullRepeats) {
        CrossSectionalBacktestApiRequest request = CrossSectionalBacktestMapper.toApiRequest(
            market, scoreVersion, dailyScoresByStock, benchmarkPrices, axis, horizonDays,
            nullTest, nullRepeats);
        CrossSectionalBacktestApiResponse response = pythonEngineClient.runCrossSectionalBacktest(request);
        return CrossSectionalBacktestMapper.toResult(
            market, axis, BacktestSampleSplit.FULL, LocalDate.now(), response);
    }

    private StockBacktestOutput toOutput(BacktestApiResponse response, LocalDate end) {
        return new StockBacktestOutput(
            BacktestMapper.toBacktestResults(response, end),
            BacktestMapper.toBacktestDailyScores(response),
            response.scoreVersion(),
            response.sampleDays());
    }
}
