"""자막 텍스트 -> 구조화 AI 요약(JSON) 생성.

Gemini + `response_schema`(Pydantic 모델)로 JSON 스키마를 강제한다 -
Anthropic Messages API처럼 프롬프트로 JSON을 "부탁"하고 수동으로
`json.loads`하는 방식이 아니라, API 레벨에서 스키마 적합성이 보장돼
malformed JSON 파싱 실패 자체가 구조적으로 없다(2026-07-29,
Gemini/Groq/OpenRouter 무료 티어 비교 검토 후 채택 - 상세 근거는
docs/CHANGELOG.md 참고. Groq는 자막 하나 분량(수천~1만여 토큰)이 무료
티어 TPM 6,000 한도를 넘거나 육박해 이 용도엔 부적합, Gemini는 1M
토큰 컨텍스트+무료 RPD가 현재 실사용량 대비 넉넉함).

모델명은 반드시 실제 API로 재검증할 것 - 처음 고른 "gemini-2.5-flash"는
`models.list()`에는 여전히 노출되지만 실제 generateContent 호출 시
"no longer available to new users"로 거부됐다(2026-07-29 라이브 호출로
확인). 세대가 빠르게 바뀌므로 학습 데이터나 문서의 모델명을 그대로
믿지 말고, 배포 전 `client.models.list()`와 실제 generate_content
호출로 매번 재확인할 것.

commentary.py(스코어 코멘트, Anthropic 유지)와 결과 처리 방식이 다르다 -
commentary.py는 요청마다 휘발되는 보조 문구라 API 장애 시 규칙 기반
템플릿으로 조용히 폴백하지만, 이 모듈의 결과는 Summary.model 컬럼에
"어떤 모델이 분석했는지"까지 영속화되는 데이터라 가짜 폴백 문구를 진짜
AI 분석인 것처럼 저장하면 안 된다(전역 CLAUDE.md "없는 데이터를 숫자로
꾸며내지 않는다" 원칙과 동일한 이유) - API 키가 없거나 호출이 실패하면
그냥 예외를 올려서 Spring이 실패로 처리(재시도 대상)하게 한다.

긴 자막(대략 5만자 초과, 실제 관측된 영상들은 전부 이 문턱 아래라
단일 호출로 충분했지만 몇 시간짜리 라이브 방송 등 예외적으로 긴 영상을
대비한 안전장치 - Gemini의 1M 토큰 컨텍스트라면 사실 굳이 안 나눠도
되지만, 청크 요약이 원본 그대로 넣는 것보다 최종 프롬프트 비용을
줄여준다는 점은 여전히 유효해 그대로 유지)은 map-reduce로 처리한다 -
transcript.fetcher의 chunk_text()로 나눈 뒤 청크별로 짧은 불릿 요약을
먼저 뽑고, 그 불릿들을 모아 최종 구조화 요약을 한 번 더 생성한다.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Literal

from google import genai
from google.genai import types
from pydantic import BaseModel, Field

from transcript.fetcher import chunk_text

# 투자 권유 아님 고지 문구는 LLM이 매번 새로 생성하지 않고 고정 문자열을 쓴다
# (2026-07-30 결정) - 컴플라이언스 성격의 문구가 호출마다 미묘하게 달라지는 걸
# 막고, 출력 스키마에서 이 필드를 아예 빼 토큰도 아낀다.
_FIXED_CAVEAT = "본 요약은 AI가 자동 생성한 정보 참고용 콘텐츠이며, 투자 권유나 매수·매도 추천이 아닙니다. 투자 판단과 그 결과에 대한 책임은 투자자 본인에게 있습니다."

# 한 영상에 태깅되는 종목 수의 하드 캡. 실제 선별(어떤 종목을 포함할지)은
# 프롬프트 지시(_PROMPT_INSTRUCTIONS)가 담당하고, 이건 그게 실패했을 때의
# 안전상한일 뿐이다 - Gemini의 maxItems 강제는 "가장 중요한 것"을 고르는 게
# 아니라 생성 순서대로 자르는 방식이라(라이브 테스트로 확인, 8개 언급된
# 문장에서 앞에서부터 5개만 남기고 뒤는 잘림), 개수 조절의 1차 수단으로 쓰면
# 안 된다.
_MAX_TAGGED_TICKERS = 7

# gemini-3.6-flash(무료 티어 계정별 하루 20회 확인, docs/CHANGELOG.md
# 2026-07-29 참고)에서 gemini-3.5-flash-lite로 전환(2026-07-30) - 무료
# RPD가 모델마다 별도 할당이라 flash-lite 쪽이 더 넉넉한지는 실제 배치로
# 확인. capability가 "minimal" 등급이라 지시사항 준수력이 낮아(자유
# 텍스트 프롬프트로만 stance 어휘를 강제했을 때 "negative" 같은 스키마
# 밖 값을 낸 적이 실제로 있었음) _TickerMentionSchema.stance를
# `Literal[...]`로 바꿔 API 레벨에서 값 자체를 강제하도록 보완.
_MODEL = "gemini-3.5-flash-lite"
_CHUNK_SUMMARY_MAX_OUTPUT_TOKENS = 300
_LONG_TRANSCRIPT_THRESHOLD = 50_000

_client: genai.Client | None = None
_client_initialized = False


def _get_client() -> genai.Client:
    global _client, _client_initialized
    if not _client_initialized:
        _client_initialized = True
        api_key = os.getenv("GEMINI_API_KEY")
        if api_key:
            _client = genai.Client(api_key=api_key)
    if _client is None:
        raise RuntimeError("GEMINI_API_KEY가 설정되지 않았습니다.")
    return _client


class _TickerMentionSchema(BaseModel):
    ticker_code: str
    ticker_name: str | None = None
    stance: Literal["BULLISH", "BEARISH", "NEUTRAL", "MENTIONED"]
    confidence: float


class _SummarySchema(BaseModel):
    summary: str
    key_points: list[str]
    mentioned_tickers: list[_TickerMentionSchema] = Field(max_length=_MAX_TAGGED_TICKERS)


@dataclass(frozen=True)
class TickerMention:
    ticker_code: str
    ticker_name: str | None
    stance: str
    confidence: float


@dataclass(frozen=True)
class SummaryResult:
    summary: str
    key_points: list[str]
    mentioned_tickers: list[TickerMention] = field(default_factory=list)
    caveat: str = _FIXED_CAVEAT
    model: str = _MODEL
    input_tokens: int = 0
    output_tokens: int = 0


_PROMPT_INSTRUCTIONS = (
    "다음은 국내/해외 주식 투자 관련 유튜브 영상의 자막입니다. summary와 "
    "key_points는 반드시 한국어로 답하세요(자막이 한국어라도 영어로 답하는 "
    "경우가 있어 명시함). 영상 핵심 내용을 2~4문장으로 요약하고, 핵심 포인트를 "
    "정리하세요.\n\n"
    "mentioned_tickers는 자막에서 이름만 스치듯 언급된 종목이 아니라, 실제로 "
    "논의(실적/주가 흐름/전망/이슈 등)된 종목만 포함하세요. 목표는 5개 "
    f"이하이고, 아무리 많아도 {_MAX_TAGGED_TICKERS}개를 넘기지 마세요 - 애매하면 "
    "빼는 쪽을 택하세요.\n\n"
    "ticker_code는 국내 종목이면 6자리 숫자 코드(예: 005930), 해외 종목이면 "
    "티커 심볼(예: AAPL)로 답하세요. 정확한 코드/심볼을 모르면 그 종목은 "
    "mentioned_tickers에서 아예 빼세요 - 틀린 값을 추측해서 채우지 마세요.\n\n"
    "confidence(0.0~1.0)는 \"이 종목이 영상에서 스치듯 언급된 게 아니라 실제로 "
    "유의미하게 논의됐다는 확신도\"를 뜻합니다 - 종목별로 실제 논의 비중에 "
    "따라 차등을 두세요(모든 종목에 같은 값을 반복해서 채우지 마세요). "
    "stance는 BULLISH/BEARISH/NEUTRAL/MENTIONED 중 하나로 답하세요.\n\n"
    "자막에 명확히 나오지 않는 구체적인 수치(가격, 등락률, 날짜 등)는 "
    "지어내지 말고, 확실하지 않으면 그 수치를 언급하지 마세요(자동 생성 자막은 "
    "숫자를 잘못 인식하는 경우가 흔합니다). 과도한 확신이나 투자 권유 표현은 "
    "피하세요."
)


def generate_summary(video_title: str, channel_name: str, transcript_content: str) -> SummaryResult:
    input_tokens = 0
    output_tokens = 0

    if len(transcript_content) > _LONG_TRANSCRIPT_THRESHOLD:
        transcript_content, chunk_input, chunk_output = _condense_long_transcript(transcript_content)
        input_tokens += chunk_input
        output_tokens += chunk_output

    client = _get_client()
    prompt = (
        f"{_PROMPT_INSTRUCTIONS}\n\n"
        f"채널명: {channel_name}\n"
        f"영상 제목: {video_title}\n"
        f"자막:\n{transcript_content}"
    )
    response = client.models.generate_content(
        model=_MODEL,
        contents=prompt,
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=_SummarySchema,
        ),
    )
    parsed: _SummarySchema = response.parsed
    if parsed is None:
        raise ValueError("Gemini 응답을 스키마에 맞춰 파싱하지 못했습니다.")

    input_tokens += response.usage_metadata.prompt_token_count or 0
    output_tokens += response.usage_metadata.candidates_token_count or 0

    tickers = [
        TickerMention(
            ticker_code=item.ticker_code, ticker_name=item.ticker_name,
            stance=item.stance, confidence=item.confidence)
        for item in parsed.mentioned_tickers
    ]

    return SummaryResult(
        summary=parsed.summary,
        key_points=list(parsed.key_points),
        mentioned_tickers=tickers,
        caveat=_FIXED_CAVEAT,
        model=_MODEL,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
    )


def _condense_long_transcript(content: str) -> tuple[str, int, int]:
    """긴 자막을 청크별 불릿 요약으로 축약한다. (축약된 텍스트, 누적 input_tokens,
    누적 output_tokens)를 반환해 호출부가 전체 토큰 사용량을 정확히 합산할 수 있게 한다."""
    client = _get_client()
    bullet_summaries = []
    total_input = 0
    total_output = 0
    for chunk in chunk_text(content, max_chars=8000, overlap_chars=200):
        response = client.models.generate_content(
            model=_MODEL,
            contents=(
                "다음은 긴 영상 자막의 일부입니다. 핵심 내용만 한국어 불릿 "
                f"3개 이내로 축약하세요(다른 설명 없이 불릿만):\n\n{chunk}"
            ),
            config=types.GenerateContentConfig(
                max_output_tokens=_CHUNK_SUMMARY_MAX_OUTPUT_TOKENS,
            ),
        )
        total_input += response.usage_metadata.prompt_token_count or 0
        total_output += response.usage_metadata.candidates_token_count or 0
        bullet_summaries.append((response.text or "").strip())
    return "\n\n".join(bullet_summaries), total_input, total_output
