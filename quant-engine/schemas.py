"""Spring <-> Python 퀀트 엔진 API 계약 (pydantic 모델)."""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel


class OhlcvItem(BaseModel):
    date: date
    open: float
    high: float
    low: float
    close: float
    volume: float


class StockScoreRequest(BaseModel):
    stock_code: str
    ohlcv: list[OhlcvItem]


class ScoreBatchRequest(BaseModel):
    stocks: list[StockScoreRequest]


class DivergenceResponse(BaseModel):
    flag: bool
    message: str | None = None


class StockScoreResponse(BaseModel):
    stock_code: str
    trend_score: float | None
    mean_reversion_score: float | None
    composite_score: float | None
    grade: str | None
    quadrant: str | None
    divergence: DivergenceResponse | None
    comment: str
    insufficient_data: bool


class ScoreBatchResponse(BaseModel):
    scores: list[StockScoreResponse]


class BacktestRequest(BaseModel):
    stock_code: str
    ohlcv: list[OhlcvItem]
    # 초과수익률 계산의 기준선(코스피/코스닥 등 벤치마크 지수 OHLCV) -
    # calculator/backtest.py의 방법론 참고.
    benchmark_ohlcv: list[OhlcvItem]


class BucketStatResponse(BaseModel):
    bucket: int
    mean_excess_return: float | None
    median_excess_return: float | None
    hit_rate: float | None
    sample_size: int


class HorizonStatResponse(BaseModel):
    horizon: int
    rank_ic: float | None
    rank_ic_ci_low: float | None
    rank_ic_ci_high: float | None
    sample_size: int
    buckets: list[BucketStatResponse]


class StabilityStatResponse(BaseModel):
    score_autocorrelation: float | None
    grade_flip_rate: float | None


class AxisBacktestResponse(BaseModel):
    axis: str
    horizons: list[HorizonStatResponse]
    stability: StabilityStatResponse


class BacktestResponse(BaseModel):
    stock_code: str
    score_version: str
    sample_days: int
    axes: list[AxisBacktestResponse]
