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
