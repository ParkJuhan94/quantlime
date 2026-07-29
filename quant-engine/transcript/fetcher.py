"""유튜브 영상 자막(캡션) 조회.

youtube-transcript-api 1.x는 인스턴스 기반 API로 재설계됐다(0.x의
정적 `get_transcript()`는 더 이상 없음). `fetch(video_id, languages=[...])`는
내부적으로 `list(video_id).find_transcript(languages).fetch()`의 축약형이고,
`find_transcript`는 languages 순서를 우선시하되 각 언어에서 수동 자막을
자동 생성 자막보다 먼저 찾는다 - 한국어 수동 자막이 있으면 그걸, 없으면
한국어 자동 자막을, 그것도 없으면 영어로 넘어가는 폴백이 라이브러리
내장 동작만으로 충분해 별도 재시도 로직이 필요 없다.

자막 자체가 없는 경우(TranscriptsDisabled/NoTranscriptFound/VideoUnavailable)는
"이 영상은 원래 자막이 없다"는 정상적인 비즈니스 결과이므로 예외를 그대로
올리지 않고 TranscriptResult(available=False)로 변환한다(가격 API가 시세
없음을 404가 아니라 200+null로 응답하는 것과 동일한 컨벤션). 반면
IP 차단(IpBlocked/RequestBlocked)이나 그 외 네트워크 오류는 호출부가
재시도할 수 있어야 하므로 예외를 그대로 전파한다.
"""

from __future__ import annotations

from dataclasses import dataclass

from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import (
    NoTranscriptFound,
    TranscriptsDisabled,
    VideoUnavailable,
)

_DEFAULT_LANGUAGES = ("ko", "en")

# 자막 자체가 없는 것으로 간주해 재시도 대상에서 제외할 예외들. IpBlocked/
# RequestBlocked 등 그 외 CouldNotRetrieveTranscript 서브클래스는 일시적
# 네트워크/차단 문제일 수 있어 여기 포함하지 않고 그대로 전파한다.
_NO_TRANSCRIPT_EXCEPTIONS = (TranscriptsDisabled, NoTranscriptFound, VideoUnavailable)


@dataclass(frozen=True)
class TranscriptResult:
    available: bool
    source: str | None = None
    lang: str | None = None
    content: str | None = None
    char_count: int | None = None
    reason: str | None = None


def fetch_transcript(video_id: str, languages: tuple[str, ...] = _DEFAULT_LANGUAGES) -> TranscriptResult:
    api = YouTubeTranscriptApi()
    try:
        fetched = api.fetch(video_id, languages=list(languages))
    except _NO_TRANSCRIPT_EXCEPTIONS as e:
        return TranscriptResult(available=False, reason=type(e).__name__)

    content = " ".join(snippet.text.strip() for snippet in fetched.snippets if snippet.text.strip())
    source = "youtube_auto_caption" if fetched.is_generated else "youtube_caption"
    return TranscriptResult(
        available=True,
        source=source,
        lang=fetched.language_code,
        content=content,
        char_count=len(content),
    )


def chunk_text(content: str, max_chars: int = 6000, overlap_chars: int = 200) -> list[str]:
    """긴 자막을 LLM 프롬프트에 넣기 좋은 크기로 나눈다(P4 AI 요약 단계에서
    사용 예정 - 아직 그 단계가 구현 전이라 여기서는 호출되지 않지만, 청킹
    전략을 지금 결정해 P4가 새로 설계할 필요 없게 준비해둔다).

    문장/구두점 경계를 존중하지 않는 단순 문자 수 기준 슬라이딩 윈도우다 -
    자막은 애초에 완전한 문장 단위가 아니라 짧은 발화 조각들을 이어붙인
    텍스트라 문장 경계 탐지의 이득이 크지 않고, overlap으로 청크 경계에서
    문맥이 잘리는 것을 어느 정도 완화한다.
    """
    if max_chars <= overlap_chars:
        raise ValueError("max_chars는 overlap_chars보다 커야 합니다.")
    if len(content) <= max_chars:
        return [content] if content else []

    chunks: list[str] = []
    start = 0
    step = max_chars - overlap_chars
    while start < len(content):
        chunks.append(content[start:start + max_chars])
        start += step
    return chunks
