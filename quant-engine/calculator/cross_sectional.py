"""횡단면(cross-sectional) Rank IC.

기존 `calculator/backtest.py`의 Rank IC는 종목 하나의 시간축 안에서만
스코어와 forward 수익률의 상관을 재는 "종목별 시계열 IC"다. 그런데 앱이
실제로 스코어를 쓰는 방식(`GET /api/dashboard/scores` 랭킹 등)은 "오늘 여러
종목 중 누가 더 점수가 높은가"를 비교하는 횡단면 비교다. 2026-08 감사
세션에서 이 둘의 계산 결과가 반대 부호로 나오는 걸 발견해(추세추종축:
종목별 시계열 IC는 음수, 직접 계산해본 횡단면 IC는 거의 0), 제품이 실제로
쓰는 질문에 맞춰 이 모듈을 새로 추가했다. 배경은 docs/CHANGELOG.md 참고.

방법론(Fama-MacBeth 스타일, quant-engine/docs/BACKTEST_METHODOLOGY_REVIEW.md의
"pooled rank IC" 요구사항):
1. 날짜별로 그날 존재하는 모든 종목을 스코어 순위 vs forward 초과수익률
   순위로 Spearman 상관(그 날짜의 횡단면 IC)을 구한다. 종목 수가
   MIN_STOCKS_PER_DATE 미만인 날은 횡단면 상관 자체가 불안정해 제외한다.
2. 날짜별 IC 시계열을 평균해 최종 IC로 보고한다.
3. 신뢰구간은 날짜별 IC 시계열에 대한 block bootstrap(연속 날짜 구간을
   통째로 리샘플)으로 낸다 - 날짜 간에도 자기상관이 있어 naive 표준오차는
   과신하게 된다.

**순환이동(circular shift) 널 테스트**: 종목별로 스코어 시계열을 무작위
오프셋만큼 회전시키면(자기상관·주변분포·수익률 시계열은 그대로 보존하고
"그 날짜의 진짜 스코어"라는 정렬만 깨뜨린다) 이 절차를 그대로 다시 돌렸을
때, 실제 관측 IC가 이 "우연/기계적 편향만으로 나올 수 있는" 분포 밖에
있는지 확인할 수 있다 - 분포 안에 있으면 관측 IC는 진짜 예측력의 증거가
아니다.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from calculator.backtest import (
    BLOCK_BOOTSTRAP_ITERATIONS,
    MIN_BOOTSTRAP_SAMPLES,
    QUANTILE_COUNT,
    WARMUP_TRADING_DAYS,
    BucketStat,
    _excess_returns_for_horizon,
    _prepare_backtest_frame,
)

# 이 미만이면 그날의 횡단면 순위상관 자체가 몇 종목만으로 결정돼 불안정하다
# (BACKTEST_METHODOLOGY_REVIEW.md 기준).
MIN_STOCKS_PER_DATE = 20
DATE_BLOCK_SIZE = 20  # 날짜 블록 부트스트랩 크기(거래일 기준 약 한 달)
NULL_TEST_REPEATS = 200


@dataclass
class CrossSectionalHorizonStat:
    horizon: int
    mean_ic: float | None
    ic_ci_low: float | None
    ic_ci_high: float | None
    n_dates: int
    n_observations: int
    buckets: list[BucketStat] = field(default_factory=list)
    null_mean: float | None = None
    null_std: float | None = None
    null_p2_5: float | None = None
    null_p97_5: float | None = None


def _stock_panel(
    stock_code: str, scores_df: pd.DataFrame, benchmark_df: pd.DataFrame,
    score_col: str, horizon: int, warmup_days: int,
) -> pd.DataFrame:
    """한 종목의 (날짜, 스코어, horizon일 뒤 초과수익률)을 df로 만든다.
    `_excess_returns_for_horizon`(backtest.py, 워밍업 제외 로직 포함)을
    그대로 재사용해 종목별 시계열 IC와 계산 로직이 갈라지지 않게 한다.
    """
    merged = _prepare_backtest_frame(scores_df, benchmark_df)
    excess_df = _excess_returns_for_horizon(merged, score_col, horizon, warmup_days)
    if excess_df.empty:
        return pd.DataFrame(columns=["date", "score", "excess_return", "stock_code"])
    excess_df = excess_df.copy()
    excess_df["stock_code"] = stock_code
    return excess_df


def build_panel(
    stocks: list[tuple[str, pd.DataFrame]], benchmark_df: pd.DataFrame, axis: str, horizon: int,
) -> pd.DataFrame:
    """여러 종목의 스코어 시계열을 받아 한 horizon·한 축에 대한 (날짜, 종목,
    스코어, 초과수익률) 통합 패널을 만든다. `stocks`는 (stock_code,
    scores_df) 튜플 리스트 - scores_df는 이미 Spring이 계산해 저장해 둔
    값이라 여기서 지표/스코어를 다시 계산하지 않는다.
    """
    score_col = "trend_score" if axis == "trend" else "mean_reversion_score"
    warmup_days = WARMUP_TRADING_DAYS[axis]
    frames = [
        _stock_panel(code, df, benchmark_df, score_col, horizon, warmup_days)
        for code, df in stocks
    ]
    frames = [f for f in frames if not f.empty]
    if not frames:
        return pd.DataFrame(columns=["date", "score", "excess_return", "stock_code"])
    return pd.concat(frames, ignore_index=True)


def _daily_cross_sectional_ic(panel: pd.DataFrame) -> pd.Series:
    if panel.empty:
        return pd.Series(dtype=float)
    counts = panel.groupby("date")["score"].transform("count")
    eligible = panel[counts >= MIN_STOCKS_PER_DATE]
    if eligible.empty:
        return pd.Series(dtype=float)

    def _corr(group: pd.DataFrame) -> float:
        if group["score"].nunique() < 2 or group["excess_return"].nunique() < 2:
            return np.nan
        return group["score"].rank().corr(group["excess_return"].rank())

    daily_ic = eligible.groupby("date").apply(_corr, include_groups=False)
    return daily_ic.dropna()


def _bootstrap_daily_ic(
    daily_ic: pd.Series, block_size: int = DATE_BLOCK_SIZE, rng: np.random.Generator | None = None,
) -> tuple[float | None, float | None]:
    n = len(daily_ic)
    if n < block_size * 2:
        return None, None
    if rng is None:
        rng = np.random.default_rng()
    values = daily_ic.to_numpy()
    num_blocks = max(1, n // block_size)
    boot_means: list[float] = []
    for _ in range(BLOCK_BOOTSTRAP_ITERATIONS):
        block_starts = rng.integers(0, n - block_size + 1, size=num_blocks)
        idx = np.concatenate([np.arange(s, s + block_size) for s in block_starts])
        boot_means.append(float(values[idx].mean()))
    if len(boot_means) < MIN_BOOTSTRAP_SAMPLES:
        return None, None
    low, high = np.percentile(boot_means, [2.5, 97.5])
    return float(low), float(high)


def _cross_sectional_buckets(panel: pd.DataFrame) -> list[BucketStat]:
    """날짜별로 5분위 버킷을 나눠 종목-일 단위 초과수익률을 모은 뒤, 버킷별로
    전 날짜 통합 평균/중위/승률을 낸다(Fama-MacBeth 포트폴리오 정렬과 동일한
    방식). `backtest.py._quantile_buckets`와 달리 버킷은 "그 종목의
    최저~최고"가 아니라 "그날 전 종목 중 최저~최고"를 뜻한다.
    """
    if panel.empty:
        return []

    def _assign(group: pd.DataFrame) -> pd.Series:
        try:
            bucket = pd.qcut(group["score"], QUANTILE_COUNT, labels=False, duplicates="drop") + 1
        except ValueError:
            return pd.Series(np.nan, index=group.index)
        if bucket.dropna().nunique() != QUANTILE_COUNT:
            return pd.Series(np.nan, index=group.index)
        return bucket

    bucketed = panel.copy()
    bucketed["bucket"] = bucketed.groupby("date", group_keys=False).apply(_assign, include_groups=False)
    bucketed = bucketed.dropna(subset=["bucket"])
    if bucketed.empty:
        return []

    result = []
    for bucket in sorted(bucketed["bucket"].unique()):
        rows = bucketed[bucketed["bucket"] == bucket]
        result.append(BucketStat(
            bucket=int(bucket),
            mean_excess_return=float(rows["excess_return"].mean()),
            median_excess_return=float(rows["excess_return"].median()),
            hit_rate=float((rows["excess_return"] > 0).mean()),
            sample_size=len(rows),
        ))
    return result


def _circular_shift_panel(panel: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    """종목별로 스코어 시계열을 무작위 오프셋만큼 순환이동한다(널 테스트용).
    각 종목의 스코어 자기상관·분포, 초과수익률 시계열은 그대로 보존하고
    "그 날짜의 진짜 스코어"만 다른 날의 스코어로 바꿔치기한다.
    """
    def _shift(group: pd.DataFrame) -> pd.DataFrame:
        n = len(group)
        shifted = group.sort_values("date").copy()
        shifted["stock_code"] = group.name
        if n < 2:
            return shifted
        offset = int(rng.integers(1, n))
        shifted["score"] = np.roll(shifted["score"].to_numpy(), offset)
        return shifted

    return panel.groupby("stock_code", group_keys=False).apply(_shift, include_groups=False)


def _horizon_stat(
    panel: pd.DataFrame, horizon: int, null_test: bool, null_repeats: int, rng: np.random.Generator,
) -> CrossSectionalHorizonStat:
    daily_ic = _daily_cross_sectional_ic(panel)
    mean_ic = float(daily_ic.mean()) if len(daily_ic) > 0 else None
    ci_low, ci_high = (
        _bootstrap_daily_ic(daily_ic, rng=rng) if mean_ic is not None else (None, None)
    )

    stat = CrossSectionalHorizonStat(
        horizon=horizon,
        mean_ic=mean_ic,
        ic_ci_low=ci_low,
        ic_ci_high=ci_high,
        n_dates=int(len(daily_ic)),
        n_observations=int(len(panel)),
        buckets=_cross_sectional_buckets(panel),
    )

    if null_test and mean_ic is not None:
        null_ics: list[float] = []
        for _ in range(null_repeats):
            shuffled_daily_ic = _daily_cross_sectional_ic(_circular_shift_panel(panel, rng))
            if len(shuffled_daily_ic) > 0:
                null_ics.append(float(shuffled_daily_ic.mean()))
        if null_ics:
            arr = np.array(null_ics)
            low, high = np.percentile(arr, [2.5, 97.5])
            stat.null_mean = float(arr.mean())
            stat.null_std = float(arr.std())
            stat.null_p2_5 = float(low)
            stat.null_p97_5 = float(high)

    return stat


def run_cross_sectional_backtest(
    stocks: list[tuple[str, pd.DataFrame]],
    benchmark_df: pd.DataFrame,
    axis: str,
    horizon: int,
    null_test: bool = False,
    null_repeats: int = NULL_TEST_REPEATS,
) -> CrossSectionalHorizonStat:
    """(축, horizon) 하나에 대해 횡단면 백테스트를 실행한다. `stocks`의 각
    scores_df는 이미 Spring이 backtest_daily_score에 저장해 둔 값을 그대로
    담고 있다고 가정한다(재계산하지 않음, main.py 참고).

    **호출당 (축, horizon) 하나로 좁힌 이유**: 처음엔 두 축 x 4 horizon(8개
    조합)을 한 호출에 묶었으나, 실제 500종목·520거래일 규모로 측정한 결과
    조합당 8~9초(널 테스트 없이도)라 8개를 한 번에 처리하면 PythonEngineClient
    read timeout(60초)을 넘겼고, 널 테스트(반복 200회)를 얹으면 조합 하나만도
    수십~백초대로 치솟았다(2026-08 감사 세션 실측). 호출을 조합 단위로
    좁히면 각 호출이 여유 있게 60초 안에 끝나고, Spring 쪽에서 조합별로
    실패를 격리할 수도 있다.
    """
    rng = np.random.default_rng()
    panel = build_panel(stocks, benchmark_df, axis, horizon)
    return _horizon_stat(panel, horizon, null_test, null_repeats, rng)
