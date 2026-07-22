"""스코어의 예측력을 실데이터로 검증하는 백테스트 통계 엔진.

방법론은 외부 리뷰(claude-fable-5)를 받아 확정했다(전문은
quant-engine/docs/BACKTEST_METHODOLOGY_REVIEW.md, gitignore 처리돼 로컬
전용):

- **진입가 = T+1 종가**: 스코어는 그날 장 마감 후 산출되므로, 같은 날(T) 종가로
  진입한다고 가정하면 아직 나오지 않은 정보로 매매하는 look-ahead bias가 된다.
- **초과수익률** = 종목 forward 수익률 - 벤치마크 forward 수익률(동일 구간) -
  단순 수익률만 보면 시장 전체가 오른 구간에서 모든 스코어가 "잘 맞은 것처럼"
  보이는 착시를 제거한다.
- **horizon 5/10/20/60일**: 평균회귀(단기)와 추세추종(중장기)은 수익 실현
  기간이 달라 두 축을 분리해 각각 전 horizon에 대해 검증한다(120일은 표본
  부족으로 제외).
- **naive 표준오차 금지**: 하루 단위로 겹치는(overlapping) 샘플은 자기상관이
  있어 일반 표준오차 공식을 쓰면 신뢰구간이 실제보다 좁게(과신) 나온다 -
  block bootstrap(연속 구간을 통째로 리샘플)으로 Rank IC의 신뢰구간을 낸다.
- Spearman 상관계수는 scipy 없이 pandas 내장(Series.corr(method="spearman"))
  으로 계산한다 - scipy는 이 프로젝트 의존성에 없고, rank 기반 상관계수
  자체는 pandas만으로 충분하다(p-value가 필요한 게 아니라 계수값만 필요).
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd

HORIZONS: list[int] = [5, 10, 20, 60]
QUANTILE_COUNT = 5
# 부트스트랩 반복 횟수와 블록 크기(거래일 기준 약 한 달) - 블록 크기가 너무
# 작으면 일별 자기상관을 보존하지 못해 naive 표준오차와 다를 바 없어진다.
BLOCK_BOOTSTRAP_ITERATIONS = 500
BLOCK_SIZE = 20
MIN_BOOTSTRAP_SAMPLES = 30  # 이 미만이면 신뢰구간 자체를 내지 않는다(부트스트랩 표본 부족)


@dataclass
class BucketStat:
    bucket: int  # 1(최저점) ~ QUANTILE_COUNT(최고점)
    mean_excess_return: float | None
    median_excess_return: float | None
    hit_rate: float | None  # 초과수익률이 양수인 표본 비율
    sample_size: int


@dataclass
class HorizonStat:
    horizon: int
    rank_ic: float | None
    rank_ic_ci_low: float | None
    rank_ic_ci_high: float | None
    sample_size: int
    buckets: list[BucketStat] = field(default_factory=list)


@dataclass
class StabilityStat:
    score_autocorrelation: float | None  # 전일 스코어와의 lag-1 자기상관
    grade_flip_rate: float | None  # 전일 대비 등급이 바뀐 거래일 비율


@dataclass
class AxisBacktestResult:
    axis: str  # "trend" | "mean_reversion"
    horizons: list[HorizonStat]
    stability: StabilityStat


def _prepare_backtest_frame(scores_df: pd.DataFrame, benchmark_df: pd.DataFrame) -> pd.DataFrame:
    """종목 스코어와 벤치마크를 날짜로 inner-join해, 이후 forward return
    계산이 위치 인덱스(오프셋)만으로 안전하게 같은 거래일을 가리키게 한다 -
    두 시계열의 거래일이 완전히 일치하지 않을 수 있어(개별 데이터 공백 등)
    단순 위치 정렬만으로는 날짜가 어긋날 수 있기 때문이다.
    """
    merged = scores_df.merge(
        benchmark_df[["date", "close"]].rename(columns={"close": "benchmark_close"}),
        on="date",
        how="inner",
    )
    return merged.sort_values("date").reset_index(drop=True)


def _forward_return(close: pd.Series, entry_idx: int, horizon: int) -> float | None:
    exit_idx = entry_idx + horizon
    if entry_idx >= len(close) or exit_idx >= len(close):
        return None
    entry_price = close.iloc[entry_idx]
    exit_price = close.iloc[exit_idx]
    if pd.isna(entry_price) or pd.isna(exit_price) or entry_price == 0:
        return None
    return float(exit_price / entry_price - 1.0)


def _excess_returns_for_horizon(merged: pd.DataFrame, score_col: str, horizon: int) -> pd.DataFrame:
    """스코어가 나온 날(T)의 다음 날(T+1) 종가를 진입가로 삼아, horizon 뒤
    청산했을 때의 종목-벤치마크 초과수익률을 각 T마다 계산한다.
    """
    stock_close = merged["close"]
    benchmark_close = merged["benchmark_close"]
    rows = []
    for t in range(len(merged)):
        score = merged[score_col].iloc[t]
        if pd.isna(score):
            continue
        entry_idx = t + 1
        stock_ret = _forward_return(stock_close, entry_idx, horizon)
        bench_ret = _forward_return(benchmark_close, entry_idx, horizon)
        if stock_ret is None or bench_ret is None:
            continue
        rows.append({"score": float(score), "excess_return": stock_ret - bench_ret})
    return pd.DataFrame(rows, columns=["score", "excess_return"])


def _spearman(df: pd.DataFrame) -> float | None:
    """Spearman 순위상관계수 = 두 변수를 순위로 변환한 뒤의 Pearson
    상관계수. pandas의 Series.corr(method="spearman")은 내부적으로
    scipy.stats.spearmanr를 임포트하는데, scipy는 이 프로젝트 의존성에
    없다(p-value가 필요한 게 아니라 계수값만 필요하므로 추가하지 않고
    rank()+기본 Pearson corr만으로 직접 계산한다).
    """
    if len(df) < 2 or df["score"].nunique() < 2 or df["excess_return"].nunique() < 2:
        return None
    ic = df["score"].rank().corr(df["excess_return"].rank())
    return None if pd.isna(ic) else float(ic)


def _rank_ic_with_bootstrap(df: pd.DataFrame) -> tuple[float | None, float | None, float | None]:
    ic = _spearman(df)
    if ic is None:
        return None, None, None

    n = len(df)
    if n < BLOCK_SIZE * 2:
        return ic, None, None

    rng = np.random.default_rng(42)
    num_blocks = max(1, n // BLOCK_SIZE)
    boot_ics: list[float] = []
    for _ in range(BLOCK_BOOTSTRAP_ITERATIONS):
        block_starts = rng.integers(0, n - BLOCK_SIZE + 1, size=num_blocks)
        idx = np.concatenate([np.arange(s, s + BLOCK_SIZE) for s in block_starts])
        boot_ic = _spearman(df.iloc[idx])
        if boot_ic is not None:
            boot_ics.append(boot_ic)

    if len(boot_ics) < MIN_BOOTSTRAP_SAMPLES:
        return ic, None, None
    low, high = np.percentile(boot_ics, [2.5, 97.5])
    return ic, float(low), float(high)


def _quantile_buckets(df: pd.DataFrame) -> list[BucketStat]:
    if df.empty:
        return []
    try:
        bucketed = df.copy()
        bucketed["bucket"] = (
            pd.qcut(bucketed["score"], QUANTILE_COUNT, labels=False, duplicates="drop") + 1
        )
    except ValueError:
        # 표본이 너무 적거나 스코어가 거의 동일해 5분위로 나눌 수 없는 경우
        # (pandas qcut이 ValueError를 던진다) - 버킷 없이 IC/샘플수만 보고한다.
        return []

    result = []
    for bucket in sorted(bucketed["bucket"].dropna().unique()):
        bucket_df = bucketed[bucketed["bucket"] == bucket]
        result.append(BucketStat(
            bucket=int(bucket),
            mean_excess_return=float(bucket_df["excess_return"].mean()),
            median_excess_return=float(bucket_df["excess_return"].median()),
            hit_rate=float((bucket_df["excess_return"] > 0).mean()),
            sample_size=len(bucket_df),
        ))
    return result


def _stability(merged: pd.DataFrame, score_col: str) -> StabilityStat:
    scores = merged[score_col]
    autocorrelation = None
    if scores.notna().sum() >= 3:
        raw = scores.autocorr(lag=1)
        autocorrelation = None if pd.isna(raw) else float(raw)

    grade_flip_rate = None
    if "grade" in merged.columns:
        grades = merged["grade"].dropna()
        if len(grades) >= 2:
            flips = (grades != grades.shift(1)).iloc[1:]
            grade_flip_rate = float(flips.mean())

    return StabilityStat(score_autocorrelation=autocorrelation, grade_flip_rate=grade_flip_rate)


def run_axis_backtest(merged: pd.DataFrame, axis: str) -> AxisBacktestResult:
    score_col = "trend_score" if axis == "trend" else "mean_reversion_score"
    horizon_stats = []
    for horizon in HORIZONS:
        excess_df = _excess_returns_for_horizon(merged, score_col, horizon)
        ic, ci_low, ci_high = _rank_ic_with_bootstrap(excess_df)
        horizon_stats.append(HorizonStat(
            horizon=horizon,
            rank_ic=ic,
            rank_ic_ci_low=ci_low,
            rank_ic_ci_high=ci_high,
            sample_size=len(excess_df),
            buckets=_quantile_buckets(excess_df),
        ))
    return AxisBacktestResult(
        axis=axis,
        horizons=horizon_stats,
        stability=_stability(merged, score_col),
    )


def run_backtest(scores_df: pd.DataFrame, benchmark_df: pd.DataFrame) -> list[AxisBacktestResult]:
    """scores_df: scorer.compute_scores() 결과(date, close, trend_score,
    mean_reversion_score, grade, ...). benchmark_df: date, close 컬럼을 가진
    벤치마크 지수 OHLCV. 추세추종/평균회귀 두 축 각각에 대해 전 horizon의
    통계를 산출한다.
    """
    merged = _prepare_backtest_frame(scores_df, benchmark_df)
    return [
        run_axis_backtest(merged, "trend"),
        run_axis_backtest(merged, "mean_reversion"),
    ]
