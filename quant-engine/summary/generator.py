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

from google import genai
from google.genai import types
from pydantic import BaseModel

from transcript.fetcher import chunk_text

_MODEL = "gemini-3.6-flash"
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
    stance: str
    confidence: float


class _SummarySchema(BaseModel):
    summary: str
    key_points: list[str]
    mentioned_tickers: list[_TickerMentionSchema]
    caveat: str


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
    caveat: str = ""
    model: str = _MODEL
    input_tokens: int = 0
    output_tokens: int = 0


_PROMPT_INSTRUCTIONS = (
    "다음은 국내 주식 투자 관련 유튜브 영상의 자막입니다. 영상 핵심 내용을 "
    "2~4문장으로 요약하고, 핵심 포인트와 언급된 종목을 정리하세요.\n\n"
    "종목코드를 정확히 모르면 mentioned_tickers에서 그 종목을 아예 빼세요 - "
    "틀린 6자리 KRX 코드를 추측해서 채우지 마세요. stance는 BULLISH/BEARISH/"
    "NEUTRAL/MENTIONED 중 하나로, confidence는 0.0~1.0로 답하세요. caveat에는 "
    "이 요약이 투자 권유가 아니라 정보 참고용이라는 고지 문구를 반드시 넣으세요. "
    "과도한 확신이나 투자 권유 표현은 피하세요."
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
        caveat=parsed.caveat,
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
