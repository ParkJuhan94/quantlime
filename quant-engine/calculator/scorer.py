"""추세추종/평균회귀 서브스코어 및 종합점수 산출.

상세 설계(공식 배경, 결정 근거)는 quant-engine/docs/SCORING_DESIGN.md 참고
(해당 문서는 gitignore 처리되어 로컬에만 존재).

아래 임계값/계수는 전부 초기값이며 TODO로 표시된 부분은 백테스트 후 튜닝이
필요하다.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

# 스코어링 로직(임계값/가중치)이 바뀔 때마다 올린다. 백테스트 결과를
# score_version으로 태깅해 튜닝 전후 비교가 가능하게 한다(Phase E/G,
# CLAUDE.md 백테스트 계획 참고) - v1(초기 균등가중) -> v2(추세추종/평균회귀
# 분리) -> v2.1(거래량 배율 대칭화 + 사분면 도입) -> v3.0(단일축 종합점수
# 금지 + MACD 분모 상대 하한 + 장기하락추세 평균회귀 게이트 + 등급을 절대
# 점수가 아닌 횡단면 백분위 기준으로 재정의 - 2026-09 잡주 상위 독식 감사
# 세션. 근거는 quant-engine/docs/SCORING_DESIGN.md 참고).
SCORE_VERSION = "v3.0"

# TODO: 초기값. 실데이터 분포 확인 후 튜닝 필요. (SCORING_DESIGN.md 참고)
VOLUME_MULTIPLIER_COEF = 0.3
VOLUME_RATIO_CLAMP_MIN = -0.5
VOLUME_RATIO_CLAMP_MAX = 1.0
DIVERGENCE_THRESHOLD = 40.0

# TODO: 초기값. 종가 대비 상대 비율로 두는 이유는 주가 수준이 종목마다
# 3자리 이상 차이 나서 절대값 하한은 고가주에 무력하고 저가주에 과하기
# 때문이다. std60이 이 값보다 작으면(초저변동성 SPAC 등) MACD z-score가
# tanh에서 즉시 포화해 미세한 드리프트만으로 100점이 나오는 문제가 있었다
# (2026-09 실측 - 해외 랭킹 상위를 차지한 SPAC 여럿의 60일 종가 표준편차가
# 종가의 0.1%대에 불과했다. 정확한 MACD 히스토그램 값 자체는 별도 검증
# 안 됨 - 표준편차가 이 정도로 작으면 미세한 히스토그램만으로도 이 경로가
# 포화된다는 구조적 문제만 확인된 것).
MACD_MIN_RELATIVE_STD = 0.002

# TODO: 초기값. 장기 하락추세(종가<120일선, 60일선<120일선) 종목의
# 평균회귀 원점수를 중심(50) 쪽으로 당기는 배율 - 1.0이면 게이트 없음,
# 0이면 무조건 50으로 수렴. RSI/%B가 "많이 빠질수록 100점"이라 구조적
# 하락주가 낙폭과대와 구분 없이 자동 만점을 받는 문제를 완화한다(2026-09
# 실측 - 거래정지 직전 국내 잡주들이 이 축만으로 랭킹 상위 독식).
DOWNTREND_GATE_FACTOR = 0.5

# TODO: 초기값. v3.0부터 이 컷오프는 raw composite_score가 아니라 횡단면
# 백분위(같은 날 국내/해외 모집단 대비 순위, quant-engine
# `/normalize/cross-section` 엔드포인트가 산출)에 적용된다 - v2.1은 절대
# 점수 기준이라 분포가 40~60에 쏠려 STRONG_BUY가 사실상 나오지 않았다
# (2026-09 실측, 국내 600종목 중 89.5%가 40~60구간, 최고점 69.5). 백분위
# 기준으로 바꾸면 상위 10%가 항상 STRONG_BUY가 되어 이 문제가 구조적으로
# 사라진다. `calculate_score`는 더 이상 이 컷오프를 쓰지 않는다(항상
# grade=None 반환) - `_grade`/`GRADE_CUTOFFS`는 정규화 단계가 재사용하도록
# 여기 남겨둔다.
GRADE_CUTOFFS: list[tuple[str, float]] = [
    ("STRONG_BUY", 90.0),
    ("BUY", 70.0),
    ("NEUTRAL", 30.0),
    ("SELL", 10.0),
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


def _macd_score(histogram: float | None, std60: float | None, close: float | None) -> float | None:
    if _is_missing(histogram) or _is_missing(std60) or _is_missing(close):
        return None
    floor = abs(close) * MACD_MIN_RELATIVE_STD
    effective_std = max(std60, floor)
    if effective_std == 0:
        return None
    z = histogram / effective_std
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


def _apply_downtrend_gate(
    mean_reversion_raw: float | None, close: float, ma_60: float | None, ma_120: float | None,
) -> float | None:
    """장기 하락추세(종가<120일선 및 60일선<120일선)에서는 평균회귀 원점수를
    중심(50) 쪽으로 당겨 억제한다.

    이산 캡(50 초과 금지) 대신 중심 기준 편차를 축소하는 연속형을 쓴 이유는
    `_apply_volume_multiplier`와 같은 패턴(dev = score - 50 스케일링)을
    재사용해 ma_120 경계에서 점수가 튀지 않게 하기 위함이다. ma_60/ma_120
    중 하나라도 계산 불가(신규상장 등)면 게이트를 적용하지 않는다 - 판단
    근거 자체가 없기 때문.
    """
    if mean_reversion_raw is None or _is_missing(ma_60) or _is_missing(ma_120):
        return mean_reversion_raw
    if close < ma_120 and ma_60 < ma_120:
        return 50.0 + (mean_reversion_raw - 50.0) * DOWNTREND_GATE_FACTOR
    return mean_reversion_raw


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
    mean_reversion_raw = _apply_downtrend_gate(
        mean_reversion_raw, close, latest.get("ma_60"), latest.get("ma_120")
    )

    macd_score = _macd_score(latest.get("macd_histogram"), latest.get("macd_histogram_std60"), close)
    ma_values = {p: latest.get(f"ma_{p}") for p in MA_PERIODS}
    ma_score = _ma_score(close, ma_values)
    trend_raw = _average_available([macd_score, ma_score])

    multiplier = _volume_multiplier(latest.get("volume_ratio"))
    trend_final = _apply_volume_multiplier(trend_raw, multiplier)
    mean_reversion_final = _apply_volume_multiplier(mean_reversion_raw, multiplier)

    # v3.0부터 두 축 중 하나라도 None이면 종합점수를 내지 않는다(단일축
    # 종합점수 금지) - 이전엔 한 축만으로 100점이 나와 SPAC 등 상장 초기라
    # 평균회귀 축이 아직 계산 안 되는 종목이 추세추종 단독 100점으로 랭킹
    # 최상위를 차지했다(2026-09 실측). 등급도 더 이상 여기서 매기지 않고
    # 횡단면 백분위 산출 단계(quant-engine `/normalize/cross-section`)로
    # 넘긴다 - raw composite는 종목마다 분포가 달라(위 GRADE_CUTOFFS 주석
    # 참고) 절대 컷오프로는 등급이 40~60에 쏠린다.
    composite = None
    if trend_final is not None and mean_reversion_final is not None:
        composite = (trend_final + mean_reversion_final) / 2.0
    grade = None
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


def compute_scores(indicator_df: pd.DataFrame) -> pd.DataFrame:
    """지표가 계산된 전 구간(indicators.compute_all_indicators 결과)에 매일의
    스코어를 산출해 DataFrame으로 반환한다.

    라이브 단건 스코어(main.py의 _score_single_stock)와 백테스트
    (calculator/backtest.py)가 이 함수를 통해 같은 calculate_score를
    공유한다 - 각 행에 그 함수를 그대로 적용할 뿐 별도 계산 로직을 두지
    않아, 둘 사이에 스코어링 결과가 어긋나는(드리프트) 것을 원천 차단한다.
    """
    records = []
    for row in indicator_df.to_dict("records"):
        result = calculate_score(row)
        records.append({
            "date": row["date"],
            "close": row["close"],
            "trend_score": result.trend_score,
            "mean_reversion_score": result.mean_reversion_score,
            "composite_score": result.composite_score,
            "grade": result.grade,
            "quadrant": result.quadrant,
            "insufficient_data": result.insufficient_data,
            "divergence_flag": result.divergence.flag if result.divergence else None,
            "divergence_message": result.divergence.message if result.divergence else None,
        })
    return pd.DataFrame.from_records(records)
