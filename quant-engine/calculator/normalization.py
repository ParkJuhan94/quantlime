"""횡단면(cross-sectional) 백분위 정규화.

같은 날짜 · 같은 모집단(국내 또는 해외) 안에서 절대 서브스코어
(trend_score/mean_reversion_score)를 상대 순위(백분위, 0~100, 높을수록
상위)로 바꾼다. `calculator/scorer.py`는 여전히 절대 점수만 산출하고,
랭킹 정렬에 실제로 쓰이는 값은 항상 이 모듈의 결과다 - v2.1까지는 절대
점수로 그대로 정렬해 v3.0 감사 세션에서 실측된 문제(단일 축 포화,
40~60 구간 밀집으로 STRONG_BUY가 사실상 안 나옴)가 그대로 랭킹 순서에
반영됐다.

같은 날짜/모집단 안에서만 비교해야 의미가 있으므로, 그 기준으로 이미 한
데 모은 리스트를 받는다고 가정한다 - 날짜·모집단(국내/해외)을 나누는
책임은 호출자(main.py 엔드포인트, 실제로는 Spring의 국내/해외 배치 스레드
분리)에 있다.
"""

from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from calculator.cross_sectional import MIN_STOCKS_PER_DATE
from calculator.scorer import _grade

# 종합 백분위 산출 시 두 축의 가중치. TODO: 초기값(균등) - Phase 4
# 백테스트로 신호가 있는 축(2026-09 감사 세션 기준 평균회귀 축이 순환이동
# 널 테스트를 통과, 추세추종 축은 미통과) 쪽으로 조정할 수 있다.
TREND_WEIGHT = 0.5
MEAN_REVERSION_WEIGHT = 0.5


@dataclass
class StockAxisScores:
    stock_code: str
    trend_score: float | None
    mean_reversion_score: float | None


@dataclass
class NormalizedStockScore:
    stock_code: str
    trend_percentile: float | None
    mean_reversion_percentile: float | None
    composite_percentile: float | None
    grade: str | None


@dataclass
class CrossSectionalNormalizationResult:
    min_sample_met: bool
    items: list[NormalizedStockScore]


def _percentile_rank(series: pd.Series) -> pd.Series:
    """높을수록 상위인 0~100 백분위. 동점은 average rank를 쓴다 - 거래정지
    등으로 동점이 몰리는 구간에서 임의 순서로 갈리지 않게 하기 위함.
    NaN은 그대로 NaN으로 남고(pandas rank 기본 동작) 다른 값의 순위에
    영향을 주지 않는다."""
    return series.rank(pct=True, method="average") * 100.0


def _none_if_nan(value: float) -> float | None:
    return None if pd.isna(value) else float(value)


def normalize_cross_section(scores: list[StockAxisScores]) -> CrossSectionalNormalizationResult:
    """스코어 리스트 하나(이미 같은 날짜·같은 모집단으로 걸러진 것)를 받아
    종목별 백분위 + 등급을 계산한다.

    표본이 MIN_STOCKS_PER_DATE 미만이면 그 날짜의 횡단면 순위 자체가
    불안정하므로(cross_sectional.py와 동일 기준 재사용) 백분위를 채우지
    않고 min_sample_met=False로 반환한다 - 호출자(Spring)는 이 경우 저장을
    건너뛰어야 한다.
    """
    if len(scores) < MIN_STOCKS_PER_DATE:
        return CrossSectionalNormalizationResult(
            min_sample_met=False,
            items=[
                NormalizedStockScore(
                    stock_code=s.stock_code,
                    trend_percentile=None,
                    mean_reversion_percentile=None,
                    composite_percentile=None,
                    grade=None,
                )
                for s in scores
            ],
        )

    df = pd.DataFrame({
        "stock_code": [s.stock_code for s in scores],
        "trend_score": pd.to_numeric(pd.Series([s.trend_score for s in scores]), errors="coerce"),
        "mean_reversion_score": pd.to_numeric(
            pd.Series([s.mean_reversion_score for s in scores]), errors="coerce"
        ),
    })

    df["trend_percentile"] = _percentile_rank(df["trend_score"])
    df["mean_reversion_percentile"] = _percentile_rank(df["mean_reversion_score"])

    # 종합 백분위는 raw composite_score를 그대로 순위화하지 않는다 - 두
    # 축을 각각 백분위로 평탄화한 뒤 가중 합산하고, 그 결과를 다시
    # 백분위화한다. raw composite를 바로 순위화하면 축별 왜곡(추세축 포화/
    # 평균회귀축 캡)을 그대로 물려받는다. 두 축 백분위가 모두 있는
    # 종목만 대상으로 한다(scorer.calculate_score의 "단일축 종합점수
    # 금지"와 같은 원칙).
    both_present = df["trend_percentile"].notna() & df["mean_reversion_percentile"].notna()
    weighted = (
        df["trend_percentile"] * TREND_WEIGHT
        + df["mean_reversion_percentile"] * MEAN_REVERSION_WEIGHT
    ).where(both_present)
    df["composite_percentile"] = _percentile_rank(weighted)
    df["grade"] = df["composite_percentile"].map(lambda v: _grade(v) if pd.notna(v) else None)

    items = [
        NormalizedStockScore(
            stock_code=row.stock_code,
            trend_percentile=_none_if_nan(row.trend_percentile),
            mean_reversion_percentile=_none_if_nan(row.mean_reversion_percentile),
            composite_percentile=_none_if_nan(row.composite_percentile),
            grade=row.grade,
        )
        for row in df.itertuples()
    ]
    return CrossSectionalNormalizationResult(min_sample_met=True, items=items)
