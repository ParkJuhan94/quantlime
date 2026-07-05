"""추세추종/평균회귀 서브스코어 및 종합점수 산출.

상세 설계(공식 배경, 결정 근거)는 quant-engine/docs/SCORING_DESIGN.md 참고
(해당 문서는 gitignore 처리되어 로컬에만 존재).

아래 임계값/계수는 전부 초기값이며 TODO로 표시된 부분은 백테스트 후 튜닝이
필요하다.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

# TODO: 초기값. 실데이터 분포 확인 후 튜닝 필요. (SCORING_DESIGN.md 참고)
VOLUME_MULTIPLIER_COEF = 0.3
VOLUME_RATIO_CLAMP_MIN = -0.5
VOLUME_RATIO_CLAMP_MAX = 1.0
DIVERGENCE_THRESHOLD = 40.0

# TODO: 초기값. 특히 SSS/SS/S 컷오프는 실데이터 분포 확인 후 조정.
GRADE_CUTOFFS: list[tuple[str, float]] = [
    ("SSS", 95.0),
    ("SS", 88.0),
    ("S", 80.0),
    ("A", 65.0),
    ("B", 50.0),
    ("C", 35.0),
]
DEFAULT_GRADE = "D"

MA_PERIODS: list[int] = [5, 10, 20, 60, 120]


@dataclass
class DivergenceInfo:
    flag: bool
    message: str | None


@dataclass
class ScoreResult:
    trend_score: float | None
    mean_reversion_score: float | None
    composite_score: float | None
    grade: str | None
    divergence: DivergenceInfo | None
    insufficient_data: bool


def _is_missing(value) -> bool:
    return value is None or (isinstance(value, float) and np.isnan(value))


def _rsi_score(rsi: float | None) -> float | None:
    if _is_missing(rsi):
        return None
    if rsi <= 30:
        return 100.0
    if rsi >= 70:
        return 0.0
    return 100.0 - (rsi - 30) / 40.0 * 100.0


def _macd_score(histogram: float | None, std60: float | None) -> float | None:
    if _is_missing(histogram) or _is_missing(std60) or std60 == 0:
        return None
    z = histogram / std60
    return 50.0 + 50.0 * float(np.tanh(z))


def _bb_score(percent_b: float | None) -> float | None:
    if _is_missing(percent_b):
        return None
    clamped = min(max(percent_b, 0.0), 1.0)
    return 100.0 - clamped * 100.0


def _ma_score(close: float, ma_values: dict[int, float | None]) -> float | None:
    valid = {p: v for p, v in ma_values.items() if not _is_missing(v)}
    if not valid:
        return None
    above_count = sum(1 for v in valid.values() if close > v)
    return (above_count / len(valid)) * 100.0


def _average_available(scores: list[float | None]) -> float | None:
    available = [s for s in scores if s is not None]
    if not available:
        return None
    return sum(available) / len(available)


def _volume_multiplier(volume_ratio: float | None) -> float:
    if _is_missing(volume_ratio):
        return 1.0
    scaled = min(
        max((volume_ratio - 1.0) / 1.0, VOLUME_RATIO_CLAMP_MIN), VOLUME_RATIO_CLAMP_MAX
    )
    return 1.0 + VOLUME_MULTIPLIER_COEF * scaled


def _apply_volume_multiplier(score: float | None, multiplier: float) -> float | None:
    if score is None:
        return None
    if score > 50:
        adjusted = score * multiplier
    elif score < 50:
        adjusted = score / multiplier
    else:
        adjusted = score
    return float(min(max(adjusted, 0.0), 100.0))


def _grade(score: float | None) -> str | None:
    if score is None:
        return None
    for label, cutoff in GRADE_CUTOFFS:
        if score >= cutoff:
            return label
    return DEFAULT_GRADE


def _divergence(trend: float | None, mean_reversion: float | None) -> DivergenceInfo | None:
    if trend is None or mean_reversion is None:
        return None
    diff = abs(trend - mean_reversion)
    if diff <= DIVERGENCE_THRESHOLD:
        return DivergenceInfo(flag=False, message=None)
    if trend > mean_reversion:
        message = "상승 추세, 단기 과열 구간(되돌림 주의)"
    else:
        message = "낙폭과대, 추세는 아직 약함(반등 시도 구간)"
    return DivergenceInfo(flag=True, message=message)


def calculate_score(latest: dict) -> ScoreResult:
    """latest: indicators.compute_all_indicators() 결과의 마지막 행(dict 형태).

    필요 키: close, rsi, macd_histogram, macd_histogram_std60,
             bollinger_percent_b, volume_ratio, ma_5, ma_10, ma_20, ma_60, ma_120
    """
    close = latest["close"]

    rsi_score = _rsi_score(latest.get("rsi"))
    bb_score = _bb_score(latest.get("bollinger_percent_b"))
    mean_reversion_raw = _average_available([rsi_score, bb_score])

    macd_score = _macd_score(latest.get("macd_histogram"), latest.get("macd_histogram_std60"))
    ma_values = {p: latest.get(f"ma_{p}") for p in MA_PERIODS}
    ma_score = _ma_score(close, ma_values)
    trend_raw = _average_available([macd_score, ma_score])

    multiplier = _volume_multiplier(latest.get("volume_ratio"))
    trend_final = _apply_volume_multiplier(trend_raw, multiplier)
    mean_reversion_final = _apply_volume_multiplier(mean_reversion_raw, multiplier)

    composite = _average_available([trend_final, mean_reversion_final])
    grade = _grade(composite)
    divergence = _divergence(trend_final, mean_reversion_final)
    insufficient = trend_final is None and mean_reversion_final is None

    return ScoreResult(
        trend_score=trend_final,
        mean_reversion_score=mean_reversion_final,
        composite_score=composite,
        grade=grade,
        divergence=divergence,
        insufficient_data=insufficient,
    )
