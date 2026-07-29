from unittest.mock import patch

import pytest
from youtube_transcript_api._errors import (
    IpBlocked,
    NoTranscriptFound,
    TranscriptsDisabled,
)
from youtube_transcript_api._transcripts import FetchedTranscript, FetchedTranscriptSnippet

from transcript.fetcher import chunk_text, fetch_transcript


def _fetched(text_parts: list[str], is_generated: bool = True, language_code: str = "ko") -> FetchedTranscript:
    snippets = [
        FetchedTranscriptSnippet(text=text, start=float(i), duration=1.0)
        for i, text in enumerate(text_parts)
    ]
    return FetchedTranscript(
        snippets=snippets, video_id="v1", language="Korean",
        language_code=language_code, is_generated=is_generated,
    )


class TestFetchTranscript:
    def test_returns_joined_content_when_available(self):
        with patch("transcript.fetcher.YouTubeTranscriptApi") as mock_api_cls:
            mock_api_cls.return_value.fetch.return_value = _fetched(["안녕하세요", "반갑습니다"])

            result = fetch_transcript("v1")

            assert result.available is True
            assert result.source == "youtube_auto_caption"
            assert result.lang == "ko"
            assert result.content == "안녕하세요 반갑습니다"
            assert result.char_count == len("안녕하세요 반갑습니다")

    def test_manually_created_caption_uses_different_source_label(self):
        with patch("transcript.fetcher.YouTubeTranscriptApi") as mock_api_cls:
            mock_api_cls.return_value.fetch.return_value = _fetched(["수동자막"], is_generated=False)

            result = fetch_transcript("v1")

            assert result.source == "youtube_caption"

    def test_skips_blank_snippets_when_joining(self):
        with patch("transcript.fetcher.YouTubeTranscriptApi") as mock_api_cls:
            mock_api_cls.return_value.fetch.return_value = _fetched(["첫줄", "  ", "둘째줄"])

            result = fetch_transcript("v1")

            assert result.content == "첫줄 둘째줄"

    @pytest.mark.parametrize("build_exception", [
        lambda: TranscriptsDisabled("v1"),
        lambda: NoTranscriptFound("v1", ["ko", "en"], None),
    ], ids=["TranscriptsDisabled", "NoTranscriptFound"])
    def test_returns_unavailable_when_no_captions_exist(self, build_exception):
        exception = build_exception()
        with patch("transcript.fetcher.YouTubeTranscriptApi") as mock_api_cls:
            mock_api_cls.return_value.fetch.side_effect = exception

            result = fetch_transcript("v1")

            assert result.available is False
            assert result.reason == type(exception).__name__
            assert result.content is None

    def test_propagates_ip_blocked_for_caller_to_retry(self):
        with patch("transcript.fetcher.YouTubeTranscriptApi") as mock_api_cls:
            mock_api_cls.return_value.fetch.side_effect = IpBlocked("v1")

            with pytest.raises(IpBlocked):
                fetch_transcript("v1")


class TestChunkText:
    def test_returns_single_chunk_when_under_limit(self):
        assert chunk_text("짧은 텍스트", max_chars=100, overlap_chars=20) == ["짧은 텍스트"]

    def test_returns_empty_list_for_empty_content(self):
        assert chunk_text("", max_chars=100, overlap_chars=20) == []

    def test_splits_into_overlapping_windows_when_over_limit(self):
        content = "가" * 250
        chunks = chunk_text(content, max_chars=100, overlap_chars=20)

        assert len(chunks) > 1
        assert all(len(c) <= 100 for c in chunks)
        # 마지막 청크를 제외한 나머지 사이에는 overlap_chars만큼 겹치는 구간이 있어야 함
        assert chunks[0][-20:] == chunks[1][:20]

    def test_reconstructs_exact_original_when_overlap_removed(self):
        # 각 청크에서 overlap 구간을 제거하고 이어붙이면 원본과 정확히 일치해야 한다
        # (경계에서 글자가 유실되거나 중복 없이 전체를 커버하는지 검증)
        content = "".join(str(i % 10) for i in range(500))
        max_chars, overlap_chars = 120, 30
        chunks = chunk_text(content, max_chars=max_chars, overlap_chars=overlap_chars)

        reconstructed = chunks[0]
        for chunk in chunks[1:]:
            reconstructed += chunk[overlap_chars:]
        assert reconstructed == content

    def test_rejects_max_chars_not_greater_than_overlap(self):
        with pytest.raises(ValueError):
            chunk_text("아무 내용", max_chars=50, overlap_chars=50)
