package com.quantlime.support;

import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.service.StockFundamentalsService;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * 무거운 연산/외부 호출 서비스를 목으로 격리하는 컨트롤러 테스트의 공용 부모.
 *
 * <p>{@code @MockBean} 조합이 다르면 Spring이 테스트마다 새 컨텍스트를 띄우는데, 이 프로젝트의
 * 컨텍스트는 Kafka 리스너 컨테이너를 6개씩 들고 있어(같은 컨슈머 그룹) 캐시된 컨텍스트가 늘수록
 * 리밸런싱 폭주로 전체 스위트가 멈춘다(2026-09-30, 컨텍스트 약 50개에서 40분 이상 무응답 확인).
 * 그래서 이런 목을 테스트 클래스마다 따로 선언하지 말고 이 부모에 모아 컨텍스트를 하나로 공유한다.
 */
public abstract class MockedServicesApiTestSupport extends ApiTestSupport {

    @MockBean
    protected ScoreService scoreService;

    @MockBean
    protected BacktestService backtestService;

    @MockBean
    protected BacktestDatasetPreparationService datasetPreparationService;

    @MockBean
    protected BacktestUniverseService universeService;

    @MockBean
    protected CrossSectionalBacktestService crossSectionalBacktestService;

    @MockBean
    protected StockFundamentalsService stockFundamentalsService;
}
