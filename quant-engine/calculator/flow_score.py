"""투자자별 수급 점수(연구용 v0).

기술 점수(`scorer.py`, v3.0)와 완전히 별개의 점수다. 종합점수·등급·랭킹에는
연결하지 않고, `research/flow_score_study.py`의 횡단면 null 테스트로 예측력을
먼저 검증한다(해외 종목에는 투자자별 데이터가 없어 v3.0의 "한 축이라도 없으면
composite None" 규칙과 충돌하기 때문). 검증을 통과한 뒤에 통합을 따로 계획한다.

한 후보(예: 외국인+기관)의 점수는 두 부분의 평균(0~100)이다.
1. 강도: 일 순매수/일 거래량 비율의 20일 평균 x를 `50 + 50*tanh(x / max(std60, 하한))`로
   변환. 분모 하한은 `scorer.MACD_MIN_RELATIVE_STD`와 같은 사고 - 변동이 거의 없는
   종목에서 미세한 순매수가 포화(0/100)를 만드는 걸 막는다.
2. 지속성: 최근 10거래일 중 순매수가 양수인 날의 비율(0~100).

날짜 t의 값은 t까지의 롤링 윈도우만 쓴다(미래 참조 없음). 윈도우 안에 결측(장중
잠정 행 등으로 순매수가 비었거나 거래량이 0인 날)이 하나라도 있으면 그날 점수는
None(NaN)이다 - 부분 윈도우로 점수를 내면 종목마다 표본이 달라져 횡단면 비교가
흔들린다. 개인 투자자는 점수에서 제외한다(외국인·기관의 반대편이라 정보가 겹침).
"""

from __future__ import annotations

import numpy as np
import pandas as pd

STRENGTH_WINDOW = 20  # 강도: 일 비율의 이동평균 길이
PERSISTENCE_WINDOW = 10  # 지속성: 순매수 양수 비율 윈도우
STD_WINDOW = 60  # 강도 분모(x의 표준편차) 윈도우
# x는 "순매수 주수 / 거래량" 비율이라 0.01 = 거래량의 1%. 이보다 변동이 작은
# 종목은 표준편차 대신 이 하한으로 나눈다.
MIN_STRENGTH_STD = 0.01
# 첫 유효 점수는 x가 처음 나오는 STRENGTH_WINDOW번째 행 + std 윈도우 STD_WINDOW행이
# 채워진 뒤(0-based 인덱스 STRENGTH_WINDOW + STD_WINDOW - 2)다. 호출자가 워밍업 제외
# 기준으로 쓴다.
FLOW_WARMUP_TRADING_DAYS = STRENGTH_WINDOW + STD_WINDOW - 1

# 후보 이름 -> 순매수 컬럼 합. 입력 df는 이 컬럼들을 가져야 한다.
FLOW_CANDIDATES: dict[str, tuple[str, ...]] = {
    "smart_money": ("foreign_net", "institution_net"),
    "foreign": ("foreign_net",),
    "institution": ("institution_net",),
    "pension_fund": ("pension_net",),
    "investment_trust": ("trust_net",),
}


def _candidate_score(net: pd.Series, volume: pd.Series) -> pd.Series:
    safe_volume = volume.where(volume > 0)
    ratio = net / safe_volume

    x = ratio.rolling(STRENGTH_WINDOW, min_periods=STRENGTH_WINDOW).mean()
    std = x.rolling(STD_WINDOW, min_periods=STD_WINDOW).std()
    denominator = std.clip(lower=MIN_STRENGTH_STD)
    strength = 50.0 + 50.0 * np.tanh(x / denominator)

    positive = (net > 0).astype(float).where(net.notna())
    persistence = positive.rolling(PERSISTENCE_WINDOW, min_periods=PERSISTENCE_WINDOW).mean() * 100.0

    return (strength + persistence) / 2.0


def compute_flow_scores(df: pd.DataFrame, candidates: dict[str, tuple[str, ...]] | None = None) -> pd.DataFrame:
    """종목 하나의 날짜 오름차순 df(`date`, `volume`, 순매수 컬럼들)로 후보별
    수급 점수 시계열을 만든다. 반환 df는 `date` + 후보 이름별 컬럼(0~100, 불가하면 NaN).
    """
    if candidates is None:
        candidates = FLOW_CANDIDATES
    ordered = df.sort_values("date").reset_index(drop=True)
    volume = ordered["volume"].astype(float)

    result = pd.DataFrame({"date": ordered["date"]})
    for name, columns in candidates.items():
        net = ordered[list(columns)].astype(float).sum(axis=1, min_count=len(columns))
        result[name] = _candidate_score(net, volume)
    return result
