from calculator.commentary import generate_comment
from calculator.scorer import DivergenceInfo, ScoreResult

_EMPTY_INDICATORS: dict = {}


def _result(**overrides) -> ScoreResult:
    base = dict(
        trend_score=70.0,
        mean_reversion_score=60.0,
        composite_score=65.0,
        grade="A",
        divergence=DivergenceInfo(flag=False, message=None),
        insufficient_data=False,
    )
    base.update(overrides)
    return ScoreResult(**base)


class TestGenerateCommentFallback:
    """ANTHROPIC_API_KEY가 없는 테스트 환경에서는 항상 템플릿 폴백을 타므로,
    폴백 로직 자체(축 존재 여부에 따른 분기)를 직접 검증한다.
    """

    def test_both_axes_present_returns_quadrant_template(self):
        # given
        result = _result(trend_score=70.0, mean_reversion_score=60.0)

        # when
        comment = generate_comment("005930", result, _EMPTY_INDICATORS)

        # then
        assert "부족" not in comment

    def test_only_trend_axis_present_mentions_partial_data(self):
        # given: 평균회귀 축은 콜드스타트로 데이터 부족
        result = _result(trend_score=70.0, mean_reversion_score=None, divergence=None)

        # when
        comment = generate_comment("005930", result, _EMPTY_INDICATORS)

        # then: 완전한 데이터 부족 문구는 아니지만, 부분 데이터 부족 사실은 언급
        assert comment != "데이터가 부족해 코멘트를 생성할 수 없습니다."
        assert "부족" in comment

    def test_only_mean_reversion_axis_present_mentions_partial_data(self):
        # given
        result = _result(trend_score=None, mean_reversion_score=80.0, divergence=None)

        # when
        comment = generate_comment("005930", result, _EMPTY_INDICATORS)

        # then
        assert comment != "데이터가 부족해 코멘트를 생성할 수 없습니다."
        assert "부족" in comment

    def test_both_axes_missing_returns_full_insufficient_message(self):
        # given
        result = _result(
            trend_score=None, mean_reversion_score=None,
            composite_score=None, grade=None, divergence=None,
            insufficient_data=True,
        )

        # when
        comment = generate_comment("005930", result, _EMPTY_INDICATORS)

        # then
        assert comment == "데이터가 부족해 코멘트를 생성할 수 없습니다."
