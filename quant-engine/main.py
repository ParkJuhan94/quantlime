"""QuantLime 퀀트 엔진 (FastAPI).

Spring 백엔드가 관심 종목의 OHLCV 이력을 넘기면, 기술적 지표를 계산해
추세추종/평균회귀 서브스코어 + 종합점수 + 등급 + AI 코멘트를 반환한다.
"""

from __future__ import annotations

import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from calculator.backtest import run_backtest
from calculator.commentary import generate_comment
from calculator.indicators import compute_all_indicators
from calculator.scorer import SCORE_VERSION, calculate_score, compute_scores
from schemas import (
    AxisBacktestResponse,
    BacktestRequest,
    BacktestResponse,
    BucketStatResponse,
    DivergenceResponse,
    HorizonStatResponse,
    ScoreBatchRequest,
    ScoreBatchResponse,
    StabilityStatResponse,
    StockScoreRequest,
    StockScoreResponse,
)

load_dotenv()

app = FastAPI(title="QuantLime Quant Engine", version="0.1.0")

# Prometheus가 스크랩할 /metrics를 노출한다(요청 수/지연을 자동 계측).
# Spring 쪽 Micrometer와 마찬가지로 멀티서비스 관측성 스택(Phase 1)의
# 일부 - quant-engine은 요청량이 적어(배치 호출 위주) 커스텀 지표보다는
# 기본 HTTP 계측만으로 충분하다고 판단해 최소 설정만 적용한다.
Instrumentator().instrument(app).expose(app)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def _score_single_stock(stock: StockScoreRequest) -> StockScoreResponse:
    df = pd.DataFrame([item.model_dump() for item in stock.ohlcv])
    df = df.sort_values("date").reset_index(drop=True)

    enriched = compute_all_indicators(df)
    latest = enriched.iloc[-1].to_dict()

    score = calculate_score(latest)
    comment = generate_comment(stock.stock_code, score, latest)

    divergence_response = None
    if score.divergence is not None:
        divergence_response = DivergenceResponse(
            flag=score.divergence.flag, message=score.divergence.message
        )

    return StockScoreResponse(
        stock_code=stock.stock_code,
        trend_score=score.trend_score,
        mean_reversion_score=score.mean_reversion_score,
        composite_score=score.composite_score,
        grade=score.grade,
        quadrant=score.quadrant,
        divergence=divergence_response,
        comment=comment,
        insufficient_data=score.insufficient_data,
    )


@app.post("/calculate/score/batch", response_model=ScoreBatchResponse)
def calculate_score_batch(request: ScoreBatchRequest) -> ScoreBatchResponse:
    results = [_score_single_stock(stock) for stock in request.stocks]
    return ScoreBatchResponse(scores=results)


@app.post("/backtest/score", response_model=BacktestResponse)
def backtest_score(request: BacktestRequest) -> BacktestResponse:
    stock_df = (
        pd.DataFrame([item.model_dump() for item in request.ohlcv])
        .sort_values("date")
        .reset_index(drop=True)
    )
    benchmark_df = (
        pd.DataFrame([item.model_dump() for item in request.benchmark_ohlcv])
        .sort_values("date")
        .reset_index(drop=True)
    )

    scores_df = compute_scores(compute_all_indicators(stock_df))
    axis_results = run_backtest(scores_df, benchmark_df)

    return BacktestResponse(
        stock_code=request.stock_code,
        score_version=SCORE_VERSION,
        sample_days=len(scores_df),
        axes=[
            AxisBacktestResponse(
                axis=axis_result.axis,
                horizons=[
                    HorizonStatResponse(
                        horizon=h.horizon,
                        rank_ic=h.rank_ic,
                        rank_ic_ci_low=h.rank_ic_ci_low,
                        rank_ic_ci_high=h.rank_ic_ci_high,
                        sample_size=h.sample_size,
                        buckets=[BucketStatResponse(**vars(b)) for b in h.buckets],
                    )
                    for h in axis_result.horizons
                ],
                stability=StabilityStatResponse(
                    score_autocorrelation=axis_result.stability.score_autocorrelation,
                    grade_flip_rate=axis_result.stability.grade_flip_rate,
                ),
            )
            for axis_result in axis_results
        ],
    )
