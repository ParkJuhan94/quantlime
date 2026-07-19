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

# TODO: 초기값. 0~100을 5등분한 균등 컷오프 - 실데이터 분포 확인 후 조정 필요.
# 투자의견 컨센서스(강력매도~강력매수)와 같은 5단계로 통일해 화면에서
# 5개 박스 중 하나로 표시한다(예전엔 7단계였음).
GRADE_CUTOFFS: list[tuple[str, float]] = [
    ("STRONG_BUY", 80.0),
    ("BUY", 60.0),
    ("NEUTRAL", 40.0),
    ("SELL", 20.0),
]
DEFAULT_GRADE = "STRONG_SELL"

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
    quadrant: str | None = None


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
    """중심(50) 기준 편차(dev)를 대칭 증폭한다.

    이전엔 score>50이면 곱하고 <50이면 나누는 방식이라 같은 거래량 조건에서
    강세/약세가 다르게 증폭됐다(예: multiplier=1.3일 때 60점은 78로,
    40점은 30.8로 - 편차 기준 2.8배 vs 1.92배 비대칭). dev 기반으로 고쳐
    50 경계의 불연속도 함께 해소한다.
    """
    if score is None:
        return None
    dev = score - 50.0
    adjusted = 50.0 + dev * multiplier
    return float(min(max(adjusted, 0.0), 100.0))


def _grade(score: float | None) -> str | None:
    if score is None:
        return None
    for label, cutoff in GRADE_CUTOFFS:
        if score >= cutoff:
            return label
    return DEFAULT_GRADE


def _classify_quadrant(trend: float, mean_reversion: float) -> str:
    """(추세추종, 평균회귀) 두 축을 4사분면으로 분류한다.

    종합점수(두 축 평균)만 보면 "상승추세 중 눌림목"(trend_up_oversold,
    교과서적 매수 후보)과 "추세 연장·과열"(trend_up_overbought)이 비슷한
    값으로 뭉개져 구분이 안 된다 - 두 축이 상반된 철학(강세=매수 vs
    과매도=매수)이라 평균만으로는 정보가 파괴되기 때문. 사분면 라벨을
    별도로 노출해 랭킹·비교 시 이 정보 손실을 보완한다. commentary.py의
    코멘트 템플릿 선택도 이 값을 그대로 재사용한다(중복 분류 방지).
    """
    trend_strong = trend > 50
    reversion_strong = mean_reversion > 50
    if trend_strong and reversion_strong:
        return "trend_up_oversold"
    if trend_strong and not reversion_strong:
        return "trend_up_overbought"
    if not trend_strong and reversion_strong:
        return "trend_down_oversold"
    return "trend_down_overbought"


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

    quadrant = None
    if trend_final is not None and mean_reversion_final is not None:
        quadrant = _classify_quadrant(trend_final, mean_reversion_final)

    return ScoreResult(
        trend_score=trend_final,
        mean_reversion_score=mean_reversion_final,
        composite_score=composite,
        grade=grade,
        divergence=divergence,
        insufficient_data=insufficient,
        quadrant=quadrant,
    )
