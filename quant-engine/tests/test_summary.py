from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest

import summary.generator as generator_module
from summary.generator import _SummarySchema, _TickerMentionSchema, generate_summary


@pytest.fixture(autouse=True)
def _reset_client_cache():
    # generator._client/_client_initialized는 commentary.py와 동일한 지연
    # 초기화 캐시 패턴이라, 테스트마다 초기화하지 않으면 이전 테스트의
    # mock 클라이언트가 다음 테스트에도 그대로 남는다.
    generator_module._client = None
    generator_module._client_initialized = False
    yield
    generator_module._client = None
    generator_module._client_initialized = False


def _fake_response(parsed, prompt_tokens: int = 100, candidates_tokens: int = 50):
    return SimpleNamespace(
        parsed=parsed,
        text=None,
        usage_metadata=SimpleNamespace(
            prompt_token_count=prompt_tokens, candidates_token_count=candidates_tokens),
    )


class TestGenerateSummary:
    def test_raises_when_api_key_missing(self, monkeypatch):
        monkeypatch.delenv("GEMINI_API_KEY", raising=False)

        with pytest.raises(RuntimeError):
            generate_summary("영상 제목", "채널명", "자막 내용")

    def test_returns_parsed_result_when_available(self, monkeypatch):
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        parsed = _SummarySchema(
            summary="요약 내용입니다.",
            key_points=["포인트1", "포인트2"],
            macro_points=["연준 9월 추가 인하 시사"],
            mentioned_tickers=[
                _TickerMentionSchema(
                    ticker_code="005930", ticker_name="삼성전자", stance="BULLISH", confidence=0.8)
            ],
        )
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(parsed)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            result = generate_summary("영상 제목", "채널명", "짧은 자막")

        assert result.summary == "요약 내용입니다."
        assert result.key_points == ["포인트1", "포인트2"]
        assert result.macro_points == ["연준 9월 추가 인하 시사"]
        assert len(result.mentioned_tickers) == 1
        assert result.mentioned_tickers[0].ticker_code == "005930"
        assert result.mentioned_tickers[0].confidence == 0.8
        assert result.model == "gemini-3.5-flash-lite"
        assert result.input_tokens == 100
        assert result.output_tokens == 50

    def test_macro_points_defaults_to_empty_when_not_provided(self, monkeypatch):
        # given: macro_points는 default_factory=list라 스키마에 명시하지 않아도
        # 생성이 깨지지 않고, 종목 특정 없이 시황만 다루는 영상이 아니면 빈 배열이어야 함
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        parsed = _SummarySchema(summary="요약", key_points=[], mentioned_tickers=[])
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(parsed)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            result = generate_summary("제목", "채널", "자막")

        assert result.macro_points == []

    def test_caveat_is_always_the_fixed_string_not_llm_generated(self, monkeypatch):
        # given: caveat 필드 자체가 더 이상 LLM 출력 스키마에 없음(2026-07-30 결정 -
        # 컴플라이언스 문구가 호출마다 미묘하게 달라지는 걸 막기 위해 고정 문자열로 통일)
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        parsed = _SummarySchema(summary="요약", key_points=[], mentioned_tickers=[])
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(parsed)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            result = generate_summary("제목", "채널", "자막")

        assert result.caveat == generator_module._FIXED_CAVEAT
        assert "투자 권유" in result.caveat

    def test_ticker_schema_enforces_max_count_at_schema_level(self):
        # given/when: Pydantic이 만드는 JSON 스키마에 maxItems가 실제로 박히는지 확인
        # (라이브 테스트로 Gemini가 이 제약을 실제로 지키는 것까지 확인했음 - 여기서는
        # 스키마 생성 자체가 깨지지 않는지만 회귀 검증)
        schema = _SummarySchema.model_json_schema()

        # then
        assert schema["properties"]["mentioned_tickers"]["maxItems"] == generator_module._MAX_TAGGED_TICKERS
        assert schema["properties"]["macro_points"]["maxItems"] == generator_module._MAX_MACRO_POINTS

    def test_returns_empty_tickers_when_none_mentioned(self, monkeypatch):
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        parsed = _SummarySchema(summary="요약", key_points=[], mentioned_tickers=[])
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(parsed)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            result = generate_summary("제목", "채널", "자막")

        assert result.mentioned_tickers == []

    def test_raises_when_response_not_parseable(self, monkeypatch):
        # given: 스키마 강제에도 불구하고 parsed가 비는 비정상 상황(라이브러리 문서상
        # 발생 가능하다고 명시된 엣지케이스)
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(None)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            with pytest.raises(ValueError):
                generate_summary("제목", "채널", "자막")

    def test_telegram_source_kind_omits_title_line_and_uses_telegram_intro(self, monkeypatch):
        # given: 텔레그램 글은 제목이 없어(video_title=None) "영상 제목" 줄 자체가
        # 빠져야 하고, 유튜브 전용 "자동 생성 자막 오인식" 캐비엇도 붙으면 안 됨
        # (인트로에서 "본문 수치를 그대로 신뢰해도 됩니다"라고 했으므로 모순 방지)
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        parsed = _SummarySchema(summary="요약", key_points=[], mentioned_tickers=[])
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(parsed)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            generate_summary(None, "채널", "게시글 본문", source_kind="telegram")

        prompt = mock_client.models.generate_content.call_args.kwargs["contents"]
        assert "영상 제목" not in prompt
        assert "텔레그램 채널 게시글" in prompt
        assert "본문:\n게시글 본문" in prompt
        assert "자동 생성 자막은 숫자를 잘못 인식" not in prompt

    def test_youtube_source_kind_includes_title_line_and_transcript_caveat(self, monkeypatch):
        # given: source_kind 기본값("youtube")은 기존 프롬프트 구성을 그대로 유지해야 함
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        parsed = _SummarySchema(summary="요약", key_points=[], mentioned_tickers=[])
        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = _fake_response(parsed)

        with patch("summary.generator.genai.Client", return_value=mock_client):
            generate_summary("영상 제목", "채널", "자막 내용")

        prompt = mock_client.models.generate_content.call_args.kwargs["contents"]
        assert "영상 제목: 영상 제목" in prompt
        assert "자막:\n자막 내용" in prompt
        assert "자동 생성 자막은 숫자를 잘못 인식" in prompt

    def test_long_transcript_uses_map_reduce_and_accumulates_tokens(self, monkeypatch):
        # given: 임계값(5만자)을 넘는 긴 자막 - 청크 요약 호출 여러 번(response_schema
        # 없이 plain text) + 최종 구조화 호출 1번. 청크 수는 chunk_text의 슬라이딩
        # 윈도우 계산에 의존하므로 정확한 횟수를 하드코딩하지 않고, config에
        # response_schema가 있는지로 "최종 호출"과 "청크 요약 호출"을 구분한다.
        monkeypatch.setenv("GEMINI_API_KEY", "test-key")
        long_content = "가" * 60_000
        final_parsed = _SummarySchema(summary="긴 영상 요약", key_points=[], mentioned_tickers=[])

        call_count = {"chunk": 0}

        def fake_generate_content(*, model, contents, config):
            if config.response_schema is None:
                call_count["chunk"] += 1
                return SimpleNamespace(
                    parsed=None, text="- 불릿 요약",
                    usage_metadata=SimpleNamespace(prompt_token_count=1000, candidates_token_count=100))
            return _fake_response(final_parsed, prompt_tokens=2000, candidates_tokens=200)

        mock_client = MagicMock()
        mock_client.models.generate_content.side_effect = fake_generate_content

        with patch("summary.generator.genai.Client", return_value=mock_client):
            result = generate_summary("제목", "채널", long_content)

        assert result.summary == "긴 영상 요약"
        assert call_count["chunk"] > 1
        assert result.input_tokens == 1000 * call_count["chunk"] + 2000
        assert result.output_tokens == 100 * call_count["chunk"] + 200
