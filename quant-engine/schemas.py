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


class DailyScoreResponse(BaseModel):
    date: date
    close: float
    trend_score: float | None
    mean_reversion_score: float | None
    composite_score: float | None
    quadrant: str | None
    grade: str | None
    divergence: DivergenceResponse | None = None
    insufficient_data: bool = False


class StockScoreSeriesResponse(BaseModel):
    stock_code: str
    daily_scores: list[DailyScoreResponse]


class ScoreSeriesBatchResponse(BaseModel):
    scores: list[StockScoreSeriesResponse]


class BacktestResponse(BaseModel):
    stock_code: str
    score_version: str
    sample_days: int
    axes: list[AxisBacktestResponse]
    # 프론트 백테스트 페이지의 가격차트 오버레이·사분면 밴드·스코어 분포
    # 히스토그램용 일별 원시 스코어 시계열(Phase F). Score 테이블은 라이브
    # 배치가 실제로 돌아간 날짜만 쌓여 있어(이 프로젝트는 아직 며칠치뿐)
    # 과거 400일치를 재현하지 못하므로, 백테스트가 이미 계산해둔
    # compute_scores(df) 결과를 그대로 함께 내려준다.
    daily_scores: list[DailyScoreResponse]


class TranscribeRequest(BaseModel):
    video_id: str


class TranscribeResponse(BaseModel):
    available: bool
    source: str | None = None
    lang: str | None = None
    content: str | None = None
    char_count: int | None = None
    reason: str | None = None


class SummarizeRequest(BaseModel):
    video_title: str
    channel_name: str
    transcript_content: str


class TickerMentionResponse(BaseModel):
    ticker_code: str
    ticker_name: str | None = None
    stance: str
    confidence: float


class SummarizeResponse(BaseModel):
    summary: str
    key_points: list[str]
    macro_points: list[str]
    mentioned_tickers: list[TickerMentionResponse]
    caveat: str
    model: str
    input_tokens: int
    output_tokens: int
