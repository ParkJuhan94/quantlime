"""서브스코어 기반 자연어 코멘트 생성.

규칙 기반으로 먼저 상태(두 축 모두/한 축만 존재/데이터 부족)를 분류한 뒤,
그 분류와 원본 지표값을 Claude Haiku에 전달해 문장만 생성한다. 숫자 계산은
전부 scorer.py에서 이미 끝난 상태로 넘어오며, 모델에게는 "설명"만 맡긴다.

ANTHROPIC_API_KEY 미설정이거나 API 호출이 실패하면 규칙 기반 템플릿
문구로 폴백한다 (배치 작업이 API 장애로 중단되지 않도록).
"""

from __future__ import annotations

import os

from anthropic import Anthropic, APIError

from calculator.scorer import ScoreResult

_MODEL = "claude-haiku-4-5"
_MAX_TOKENS = 200

_client: Anthropic | None = None
_client_initialized = False


def _get_client() -> Anthropic | None:
    global _client, _client_initialized
    if _client_initialized:
        return _client
    _client_initialized = True
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if api_key:
        _client = Anthropic(api_key=api_key)
    return _client


def _classify_quadrant(trend_score: float, mean_reversion_score: float) -> str:
    trend_strong = trend_score > 50
    reversion_strong = mean_reversion_score > 50
    if trend_strong and reversion_strong:
        return "trend_up_oversold"
    if trend_strong and not reversion_strong:
        return "trend_up_overbought"
    if not trend_strong and reversion_strong:
        return "trend_down_oversold"
    return "trend_down_overbought"


_TEMPLATE_FALLBACK: dict[str, str] = {
    "trend_up_oversold": "상승 추세와 저평가 신호가 동시에 나타나고 있어 매수 관점에서 긍정적인 구간입니다.",
    "trend_up_overbought": "상승 추세는 유지되고 있으나 단기적으로 과열된 상태라 되돌림에 유의할 필요가 있습니다.",
    "trend_down_oversold": "낙폭이 과도해 반등 가능성이 있으나, 추세 자체는 아직 하락 흐름에서 벗어나지 못했습니다.",
    "trend_down_overbought": "추세와 단기 지표 모두 약세를 가리키고 있어 신중한 접근이 필요합니다.",
}

# 한 축만 계산 가능한 신규/소형 종목용 폴백 (콜드스타트)
_TEMPLATE_TREND_ONLY: dict[str, str] = {
    "strong": "평균회귀 지표는 아직 산출할 데이터가 부족하지만, 추세추종 지표만 보면 강세 흐름입니다.",
    "weak": "평균회귀 지표는 아직 산출할 데이터가 부족하지만, 추세추종 지표만 보면 약세 흐름입니다.",
}
_TEMPLATE_MEAN_REVERSION_ONLY: dict[str, str] = {
    "strong": "추세추종 지표는 아직 산출할 데이터가 부족하지만, 평균회귀 지표만 보면 과매도에 가깝습니다.",
    "weak": "추세추종 지표는 아직 산출할 데이터가 부족하지만, 평균회귀 지표만 보면 과매수에 가깝습니다.",
}


def _fallback_comment(score: ScoreResult) -> str:
    if score.trend_score is not None and score.mean_reversion_score is not None:
        quadrant = _classify_quadrant(score.trend_score, score.mean_reversion_score)
        return _TEMPLATE_FALLBACK[quadrant]
    if score.trend_score is not None:
        return _TEMPLATE_TREND_ONLY["strong" if score.trend_score > 50 else "weak"]
    if score.mean_reversion_score is not None:
        return _TEMPLATE_MEAN_REVERSION_ONLY[
            "strong" if score.mean_reversion_score > 50 else "weak"
        ]
    return "데이터가 부족해 코멘트를 생성할 수 없습니다."


def _build_prompt(stock_code: str, score: ScoreResult, indicators: dict) -> str:
    divergence_text = "없음"
    if score.divergence and score.divergence.flag:
        divergence_text = f"있음 - {score.divergence.message}"

    trend_text = (
        f"{score.trend_score:.1f} (50 초과=강세)"
        if score.trend_score is not None
        else "데이터 부족으로 산출 불가"
    )
    reversion_text = (
        f"{score.mean_reversion_score:.1f} (50 초과=과매도 근접)"
        if score.mean_reversion_score is not None
        else "데이터 부족으로 산출 불가"
    )

    return (
        "다음은 이미 계산이 끝난 국내 주식 기술적 지표 점수입니다. "
        "숫자를 다시 계산하지 말고, 아래 정보만 근거로 1~3문장의 자연스러운 "
        "한국어 코멘트를 작성하세요. 일부 축이 '산출 불가'이면 그 사실을 언급하고 "
        "나머지 정보만으로 서술하세요. 과도한 확신이나 투자 권유 표현은 피하세요.\n\n"
        f"종목코드: {stock_code}\n"
        f"추세추종 점수: {trend_text}\n"
        f"평균회귀 점수: {reversion_text}\n"
        f"종합 등급: {score.grade}\n"
        f"괴리 여부: {divergence_text}\n"
        f"RSI: {indicators.get('rsi')}\n"
        f"MACD 히스토그램: {indicators.get('macd_histogram')}\n"
        f"볼린저 %B: {indicators.get('bollinger_percent_b')}\n"
        f"거래량비율: {indicators.get('volume_ratio')}\n"
    )


def generate_comment(stock_code: str, score: ScoreResult, indicators: dict) -> str:
    """서브스코어/괴리도/원본 지표값을 바탕으로 1~3문장 한국어 코멘트를 생성한다.

    두 축이 전부 없을 때만 "데이터 부족" 문구를 반환하고, 한 축만 있어도
    그 축 기준으로 코멘트를 생성한다(insufficient_data와 일관되도록).
    """
    if score.insufficient_data:
        return "데이터가 부족해 코멘트를 생성할 수 없습니다."

    fallback = _fallback_comment(score)

    client = _get_client()
    if client is None:
        return fallback

    try:
        response = client.messages.create(
            model=_MODEL,
            max_tokens=_MAX_TOKENS,
            messages=[{"role": "user", "content": _build_prompt(stock_code, score, indicators)}],
        )
        text = "".join(block.text for block in response.content if block.type == "text").strip()
        return text or fallback
    except APIError:
        return fallback
    except Exception:
        return fallback
