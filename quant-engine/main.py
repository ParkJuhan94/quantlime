"""QuantLime 퀀트 엔진 (FastAPI).

Spring 백엔드가 종목의 OHLCV 이력을 넘기면, 기술적 지표를 계산해 날짜별
추세추종/평균회귀 서브스코어 + 종합점수 + 등급 시계열을 반환한다.

AI 코멘트 생성(calculator/commentary.py)은 날짜별 스코어 이력 도입과 함께
당분간 호출하지 않는다(수천 종목×수백 일마다 LLM 호출은 비용/속도상
비합리적) - 재도입 여부는 별도 TODO(docs/ROADMAP.md 참고).
"""

from __future__ import annotations

import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from calculator.backtest import run_backtest
from calculator.indicators import compute_all_indicators
from calculator.scorer import SCORE_VERSION, compute_scores
from schemas import (
    AxisBacktestResponse,
    BacktestRequest,
    BacktestResponse,
    BucketStatResponse,
    DailyScoreResponse,
    DivergenceResponse,
    HorizonStatResponse,
    ScoreBatchRequest,
    ScoreSeriesBatchResponse,
    StabilityStatResponse,
    StockScoreRequest,
    StockScoreSeriesResponse,
    TranscribeRequest,
    TranscribeResponse,
)
from transcript.fetcher import fetch_transcript

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


def _clean(value):
    """compute_scores가 채운 None이 DataFrame 왕복 중 pandas에 의해 NaN으로
    바뀌는 경우가 있어(특히 object dtype 컬럼도 예외 아님), pydantic
    직렬화 전에 다시 None으로 되돌린다 - NaN은 유효한 JSON이 아니다."""
    return None if pd.isna(value) else value


def _to_daily_score_response(row: dict) -> DailyScoreResponse:
    trend_score = _clean(row.get("trend_score"))
    mean_reversion_score = _clean(row.get("mean_reversion_score"))
    composite_score = _clean(row.get("composite_score"))
    divergence_flag = _clean(row.get("divergence_flag"))
    divergence = None
    if divergence_flag is not None:
        divergence = DivergenceResponse(
            flag=bool(divergence_flag), message=_clean(row.get("divergence_message"))
        )
    return DailyScoreResponse(
        date=row["date"],
        close=float(row["close"]),
        trend_score=float(trend_score) if trend_score is not None else None,
        mean_reversion_score=float(mean_reversion_score) if mean_reversion_score is not None else None,
        composite_score=float(composite_score) if composite_score is not None else None,
        quadrant=_clean(row.get("quadrant")),
        grade=_clean(row.get("grade")),
        divergence=divergence,
        insufficient_data=bool(row.get("insufficient_data", False)),
    )


def _compute_score_series(stock: StockScoreRequest) -> list[DailyScoreResponse]:
    df = (
        pd.DataFrame([item.model_dump() for item in stock.ohlcv])
        .sort_values("date")
        .reset_index(drop=True)
    )
    scores_df = compute_scores(compute_all_indicators(df))
    return [_to_daily_score_response(row) for row in scores_df.to_dict("records")]


@app.post("/calculate/score/series", response_model=ScoreSeriesBatchResponse)
def calculate_score_series(request: ScoreBatchRequest) -> ScoreSeriesBatchResponse:
    """종목별 OHLCV 전 구간을 한 번에 넣으면 날짜별 스코어 시계열을 통째로
    반환한다 - Spring이 "오늘자 갱신"과 "과거 결측일 백필"을 같은 호출로
    처리할 수 있게 한다(지표가 전부 과거방향 롤링 윈도우라 각 날짜 값이
    그 시점 기준으로 그대로 유효하므로, 날짜별로 반복 호출할 필요가 없다).
    """
    results = [
        StockScoreSeriesResponse(stock_code=stock.stock_code, daily_scores=_compute_score_series(stock))
        for stock in request.stocks
    ]
    return ScoreSeriesBatchResponse(scores=results)


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
        daily_scores=[_to_daily_score_response(row) for row in scores_df.to_dict("records")],
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


@app.post("/transcribe", response_model=TranscribeResponse)
def transcribe(request: TranscribeRequest) -> TranscribeResponse:
    """자막이 아예 없는 영상은 정상적인 비즈니스 결과이므로 200 +
    available=false로 응답한다(§6 현재가 API의 "404 아니라 200+null"
    컨벤션과 동일). IP 차단 등 그 외 예외는 그대로 올라가 FastAPI가
    500으로 응답하고, Spring 쪽에서 재시도 대상으로 처리한다."""
    result = fetch_transcript(request.video_id)
    return TranscribeResponse(**vars(result))
