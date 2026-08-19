"""Spring <-> Python 퀀트 엔진 API 계약 (pydantic 모델)."""

from __future__ import annotations

from datetime import date
from typing import Literal

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


class DailyScorePointInput(BaseModel):
    date: date
    close: float
    trend_score: float | None
    mean_reversion_score: float | None


class StockDailyScoreInput(BaseModel):
    stock_code: str
    daily_scores: list[DailyScorePointInput]


class CrossSectionalBacktestRequest(BaseModel):
    """이미 backtest_daily_score에 저장된 종목별 일별 스코어를 그대로 받아
    (축, horizon) 하나에 대한 횡단면(cross-sectional) IC를 계산한다 - OHLCV
    재조회·지표/스코어 재계산이 없다(calculator/cross_sectional.py 모듈
    docstring 참고).

    **호출당 (축, horizon) 하나로 좁힌 이유**: 500종목·520거래일 규모로
    실측한 결과 8개 조합(축2 x horizon4)을 한 호출에 묶으면 널 테스트 없이도
    PythonEngineClient read timeout(60초)을 넘겼다(2026-08 감사 세션).
    """
    market: str
    score_version: str
    stocks: list[StockDailyScoreInput]
    benchmark_ohlcv: list[OhlcvItem]
    axis: Literal["trend", "mean_reversion"]
    horizon: int
    null_test: bool = False
    null_repeats: int = 200


class CrossSectionalBacktestResponse(BaseModel):
    market: str
    score_version: str
    stock_count: int
    axis: str
    horizon: int
    mean_ic: float | None
    ic_ci_low: float | None
    ic_ci_high: float | None
    n_dates: int
    n_observations: int
    buckets: list[BucketStatResponse]
    null_mean: float | None = None
    null_std: float | None = None
    null_p2_5: float | None = None
    null_p97_5: float | None = None


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
    # 텔레그램 글은 제목이 없는 짧은 게시물이라 video_title 없이 채널명+본문만
    # 넘어온다(Phase 8 P7-4). source_kind 기본값을 "youtube"로 둬 기존 Java
    # 호출부(SummarizeApiRequest 3-arg 생성자)가 이 필드를 안 보내도 그대로
    # 동작한다.
    video_title: str | None = None
    channel_name: str
    transcript_content: str
    source_kind: Literal["youtube", "telegram"] = "youtube"


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
