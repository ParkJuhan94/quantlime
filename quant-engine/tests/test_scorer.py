import math

import numpy as np
import pandas as pd
import pytest

from calculator.indicators import compute_all_indicators
from calculator.scorer import (
    DIVERGENCE_THRESHOLD,
    GRADE_CUTOFFS,
    MACD_MIN_RELATIVE_STD,
    _apply_downtrend_gate,
    _apply_volume_multiplier,
    _grade,
    _macd_score,
    calculate_score,
    compute_scores,
)


def _base_latest(**overrides) -> dict:
    latest = {
        "close": 100.0,
        "rsi": 50.0,
        "macd_histogram": 0.0,
        "macd_histogram_std60": 1.0,
        "bollinger_percent_b": 0.5,
        "volume_ratio": 1.0,
        "ma_5": 100.0,
        "ma_10": 100.0,
        "ma_20": 100.0,
        "ma_60": 100.0,
        "ma_120": 100.0,
    }
    latest.update(overrides)
    return latest


class TestCalculateScoreHappyPath:
    def test_neutral_indicators_produce_neutral_scores(self):
        # given: 모든 지표가 중립값. MA는 '개수 비율'이라 5개 중 몇 개가 위에
        # 있는지로만 결정되므로 정확히 50을 만들 수 없다(0/20/40/60/80/100만
        # 가능) - 순수 중립값 검증을 위해 이 테스트에선 제외한다.
        latest = _base_latest(ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None)

        # when
        result = calculate_score(latest)

        # then
        assert result.trend_score == pytest.approx(50.0)
        assert result.mean_reversion_score == pytest.approx(50.0)
        assert result.composite_score == pytest.approx(50.0)
        # 등급은 raw composite(절대점수) 기준 - GRADE_CUTOFFS(65/55/45/35)상
        # 50은 NEUTRAL 구간. 아래 TestGradeCutoffs 참고.
        assert result.grade == "NEUTRAL"
        assert result.insufficient_data is False

    def test_oversold_rsi_and_bb_boost_mean_reversion(self):
        # given: RSI 과매도(20) + %B 하단(0)
        latest = _base_latest(rsi=20.0, bollinger_percent_b=0.0)

        # when
        result = calculate_score(latest)

        # then
        assert result.mean_reversion_score == pytest.approx(100.0)

    def test_strong_uptrend_boosts_trend_score(self):
        # given: 종가가 5개 이평선 모두 위, MACD 히스토그램 강한 양수
        latest = _base_latest(
            close=150.0,
            macd_histogram=5.0,
            macd_histogram_std60=1.0,
        )

        # when
        result = calculate_score(latest)

        # then: MA_score=100(전부 위), MACD_score는 tanh(5)->거의 100에 근접
        assert result.trend_score > 95.0


class TestCalculateScoreColdStart:
    def test_missing_macd_redistributes_trend_weight_to_ma(self):
        # given: MACD 계산 불가(데이터 부족), MA는 계산 가능
        latest = _base_latest(macd_histogram=None, macd_histogram_std60=None, close=150.0)

        # when
        result = calculate_score(latest)

        # then: 추세추종 점수가 MA_score(100)만으로 산출됨
        assert result.trend_score == pytest.approx(100.0)

    def test_missing_ma120_only_excludes_that_ma(self):
        # given: MA120만 데이터 부족(신규상장 종목), 나머지 4개 이평선은 정상.
        # MACD도 제외해 MA_score만으로 결과를 검증한다.
        latest = _base_latest(
            ma_120=None, close=150.0,
            macd_histogram=None, macd_histogram_std60=None,
        )

        # when
        result = calculate_score(latest)

        # then: 나머지 4개 이평선 기준으로 100점(전부 위)
        assert result.trend_score == pytest.approx(100.0)

    def test_all_trend_indicators_missing_yields_no_composite_score(self):
        # given: 추세추종 축 전체 데이터 부족(평균회귀 축만 계산 가능)
        latest = _base_latest(
            macd_histogram=None,
            macd_histogram_std60=None,
            ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None,
            rsi=20.0,
        )

        # when
        result = calculate_score(latest)

        # then: v3.0부터 단일 축 종합점수를 금지한다 - 이전엔 이 경우
        # composite_score가 mean_reversion_score 값을 그대로 물려받아,
        # 평균회귀 축이 아직 없는 신규상장 SPAC 등이 추세추종 단독 100점을
        # 종합점수로 노출해 랭킹 최상위를 차지하는 문제가 있었다.
        assert result.trend_score is None
        assert result.mean_reversion_score is not None
        assert result.composite_score is None
        assert result.grade is None
        assert result.insufficient_data is False

    def test_both_axes_missing_marks_insufficient_data(self):
        # given: 두 축 전부 계산 불가 (극단적 신규상장 종목)
        latest = _base_latest(
            rsi=None, bollinger_percent_b=None,
            macd_histogram=None, macd_histogram_std60=None,
            ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None,
        )

        # when
        result = calculate_score(latest)

        # then
        assert result.trend_score is None
        assert result.mean_reversion_score is None
        assert result.composite_score is None
        assert result.grade is None
        assert result.divergence is None
        assert result.insufficient_data is True


class TestVolumeMultiplier:
    def test_high_volume_amplifies_dominant_axis(self):
        # given: 추세추종이 우세(>50)한 상태에서 거래량 2배
        latest = _base_latest(close=150.0, volume_ratio=2.0)

        # when
        result_high_volume = calculate_score(latest)
        result_normal_volume = calculate_score(_base_latest(close=150.0, volume_ratio=1.0))

        # then: 거래량 급증 시 우세 축(추세추종) 점수가 더 증폭됨
        assert result_high_volume.trend_score >= result_normal_volume.trend_score

    def test_neutral_score_unaffected_by_volume(self):
        # given: 정확히 50점(중립)인 축은 거래량과 무관하게 유지.
        # MA는 이산값이라 정확히 50이 될 수 없으므로 제외하고 MACD만으로 검증.
        latest = _base_latest(
            volume_ratio=2.0,
            ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None,
        )

        # when
        result = calculate_score(latest)

        # then
        assert result.trend_score == pytest.approx(50.0)
        assert result.mean_reversion_score == pytest.approx(50.0)

    def test_multiplier_amplifies_symmetric_deviation_from_50(self):
        # given: multiplier=1.3(거래량비율 2.0 -> 1+0.3*1)일 때, 50 기준
        # 같은 크기(10)만큼 떨어진 60점과 40점 - 이전 버그(>50 곱하기,
        # <50 나누기)였다면 60->78(편차 28)/40->30.77(편차 19.23)로
        # 비대칭이었다. dev 기반 수정 후에는 편차가 대칭으로 증폭돼야 한다.
        multiplier = 1.3

        above = _apply_volume_multiplier(60.0, multiplier)
        below = _apply_volume_multiplier(40.0, multiplier)

        assert above - 50.0 == pytest.approx(50.0 - below)
        assert above == pytest.approx(63.0)
        assert below == pytest.approx(37.0)

    def test_multiplier_has_no_discontinuity_at_50(self):
        # given/when: 50에 근접한 두 점수(49.999, 50.001)에 같은 배율 적용
        multiplier = 1.3
        just_below = _apply_volume_multiplier(49.999, multiplier)
        just_above = _apply_volume_multiplier(50.001, multiplier)

        # then: 이전 버그(50 경계에서 곱셈<->나눗셈 전환)와 달리 연속적이어야 함
        assert just_above - just_below == pytest.approx(0.0026, abs=1e-4)


class TestQuadrant:
    def test_trend_up_reversion_up_is_pullback_quadrant(self):
        # given: 추세추종·평균회귀 둘 다 50 초과(상승추세 중 눌림목)
        latest = _base_latest(
            close=150.0, macd_histogram=5.0, rsi=20.0, bollinger_percent_b=0.0,
        )

        result = calculate_score(latest)

        assert result.trend_score > 50
        assert result.mean_reversion_score > 50
        assert result.quadrant == "trend_up_oversold"

    def test_trend_up_reversion_down_is_overheated_quadrant(self):
        # given: 추세추종은 강세, 평균회귀는 과매수(약세)
        latest = _base_latest(
            close=150.0, macd_histogram=5.0, rsi=90.0, bollinger_percent_b=1.0,
        )

        result = calculate_score(latest)

        assert result.trend_score > 50
        assert result.mean_reversion_score < 50
        assert result.quadrant == "trend_up_overbought"

    def test_trend_down_reversion_up_is_oversold_weak_trend_quadrant(self):
        # given: 추세추종은 약세, 평균회귀는 과매도(강세 신호)
        latest = _base_latest(
            close=50.0, macd_histogram=-5.0, rsi=10.0, bollinger_percent_b=0.0,
        )

        result = calculate_score(latest)

        assert result.trend_score < 50
        assert result.mean_reversion_score > 50
        assert result.quadrant == "trend_down_oversold"

    def test_trend_down_reversion_down_is_no_bounce_quadrant(self):
        # given: 추세추종·평균회귀 둘 다 50 미만
        latest = _base_latest(
            close=50.0, macd_histogram=-5.0, rsi=90.0, bollinger_percent_b=1.0,
        )

        result = calculate_score(latest)

        assert result.trend_score < 50
        assert result.mean_reversion_score < 50
        assert result.quadrant == "trend_down_overbought"

    def test_quadrant_is_none_when_either_axis_missing(self):
        # given: 평균회귀 축 데이터 부족
        latest = _base_latest(rsi=None, bollinger_percent_b=None)

        result = calculate_score(latest)

        assert result.mean_reversion_score is None
        assert result.quadrant is None


class TestDivergence:
    def test_large_gap_sets_divergence_flag(self):
        # given: 추세추종은 매우 강세, 평균회귀는 과매수(약세)로 괴리 발생
        latest = _base_latest(
            close=150.0,
            macd_histogram=5.0,
            rsi=90.0,
            bollinger_percent_b=1.0,
        )

        # when
        result = calculate_score(latest)

        # then
        assert abs(result.trend_score - result.mean_reversion_score) > DIVERGENCE_THRESHOLD
        assert result.divergence.flag is True
        assert "과열" in result.divergence.message

    def test_small_gap_has_no_divergence_flag(self):
        # given: 두 축 점수가 근접
        latest = _base_latest()

        # when
        result = calculate_score(latest)

        # then
        assert result.divergence.flag is False
        assert result.divergence.message is None


class TestComputeScores:
    def _synthetic_ohlcv(self, days: int = 150) -> pd.DataFrame:
        dates = pd.date_range("2026-01-01", periods=days, freq="B")
        rng = np.random.default_rng(0)
        close = pd.Series(100 + np.cumsum(rng.normal(0, 1, size=days)))
        return pd.DataFrame({
            "date": dates,
            "open": close,
            "high": close + 1,
            "low": close - 1,
            "close": close,
            "volume": rng.integers(1000, 2000, size=days),
        })

    def test_compute_scores_reuses_calculate_score_per_row(self):
        # given: compute_scores는 calculate_score를 행 단위로 그대로
        # 적용할 뿐이므로, 마지막 행 결과가 calculate_score 직접 호출과
        # 정확히 같아야 한다(라이브/백테스트 드리프트 방지가 핵심 설계 의도).
        enriched = compute_all_indicators(self._synthetic_ohlcv())

        # when
        scores_df = compute_scores(enriched)

        # then
        assert len(scores_df) == len(enriched)
        assert list(scores_df.columns) == [
            "date", "close", "trend_score", "mean_reversion_score",
            "composite_score", "grade", "quadrant", "insufficient_data",
            "divergence_flag", "divergence_message",
        ]
        direct_result = calculate_score(enriched.iloc[-1].to_dict())
        last_scored = scores_df.iloc[-1]
        assert last_scored["trend_score"] == pytest.approx(direct_result.trend_score)
        assert last_scored["mean_reversion_score"] == pytest.approx(direct_result.mean_reversion_score)
        assert last_scored["grade"] == direct_result.grade

    def test_compute_scores_marks_warmup_rows_insufficient(self):
        # given: 120일 이평(콜드스타트 요구치 중 가장 긴)이 아직 안 나오는
        # 초반 구간
        enriched = compute_all_indicators(self._synthetic_ohlcv())

        # when
        scores_df = compute_scores(enriched)

        # then: 최소 두 지표(RSI, MACD)조차 계산 안 되는 아주 초반 행은
        # 두 축 모두 None -> insufficient_data True
        assert bool(scores_df.iloc[0]["insufficient_data"]) is True


class TestCalculateScoreGradesFromAbsoluteComposite:
    def test_calculate_score_assigns_grade_from_raw_composite(self):
        # given: 두 축 모두 강하게 매수 신호(평균회귀 100 근방, 추세도 100 근방)
        result = calculate_score(
            _base_latest(rsi=15.0, bollinger_percent_b=0.0, macd_histogram=10.0, close=150.0)
        )

        # then: 등급은 raw composite_score(절대점수) 기준으로 즉시 매겨진다
        # - 시장 전체 분포와 무관하게 이 종목 자체의 점수만으로 결정된다.
        assert result.composite_score is not None
        assert result.grade == _grade(result.composite_score)

    def test_none_composite_yields_none_grade(self):
        # given: 단일축 종합점수 금지로 composite가 None인 케이스
        latest = _base_latest(
            macd_histogram=None,
            macd_histogram_std60=None,
            ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None,
            rsi=20.0,
        )

        # when
        result = calculate_score(latest)

        # then
        assert result.composite_score is None
        assert result.grade is None


class TestGradeCutoffs:
    # 등급은 raw composite_score(0~100, 절대점수)에 적용된다 - 횡단면
    # 백분위(compositePercentile)와는 척도가 다른 별개 값이다(정렬은 백분위,
    # 등급은 절대점수 - normalization.py 모듈 docstring 참고). 아래 숫자는
    # Phase 4 재보정 전 임시값(scorer.py GRADE_CUTOFFS 주석 참고).
    @pytest.mark.parametrize(
        "score,expected_grade",
        [
            (70.0, "STRONG_BUY"),
            (65.0, "STRONG_BUY"),  # 경계값 포함
            (64.9, "BUY"),
            (55.0, "BUY"),
            (54.9, "NEUTRAL"),
            (45.0, "NEUTRAL"),
            (44.9, "SELL"),
            (35.0, "SELL"),
            (34.9, "STRONG_SELL"),
            (0.0, "STRONG_SELL"),
        ],
    )
    def test_grade_matches_absolute_score_tier(self, score, expected_grade):
        assert _grade(score) == expected_grade

    def test_grade_is_none_when_score_missing(self):
        assert _grade(None) is None

    def test_cutoffs_are_defined_on_absolute_scale(self):
        labels = [label for label, _ in GRADE_CUTOFFS]
        cutoffs = [cutoff for _, cutoff in GRADE_CUTOFFS]
        assert labels == ["STRONG_BUY", "BUY", "NEUTRAL", "SELL"]
        assert cutoffs == [65.0, 55.0, 45.0, 35.0]


class TestMacdRelativeStdFloor:
    def test_near_zero_std_no_longer_saturates_score(self):
        # given: 2026-09 실측(SAMO 등 초저변동성 SPAC - 60일 종가 표준편차가
        # 종가의 0.1%대)과 구조적으로 동일한 케이스를 라운드 넘버로 구성.
        # std60(0.001)이 종가 대비 상대 하한(10.0*0.002=0.02)보다 훨씬
        # 작은 초저변동성 상황에서, 하한이 없다면 z=histogram/std60=20으로
        # tanh가 완전히 포화해 100점이 나온다.
        histogram, std60, close = 0.02, 0.001, 10.0
        without_floor_z = histogram / std60
        assert 50.0 + 50.0 * math.tanh(without_floor_z) == pytest.approx(100.0, abs=0.01)

        # when
        score = _macd_score(histogram=histogram, std60=std60, close=close)

        # then: 종가 대비 상대 하한(MACD_MIN_RELATIVE_STD)이 std60을
        # 대체해 z가 20 대신 1.0(=0.02/0.02)로 줄어들고, 포화되지 않는다.
        floor = close * MACD_MIN_RELATIVE_STD
        expected_z = histogram / floor
        assert score == pytest.approx(50.0 + 50.0 * math.tanh(expected_z))
        assert score < 90.0  # 포화(100점 근방)되지 않음

    def test_large_std_is_unaffected_by_floor(self):
        # given: 정상적인 변동성(std60=1.0)에서는 하한이 개입하지 않아야 함
        score = _macd_score(histogram=5.0, std60=1.0, close=100.0)

        # then: 기존과 동일하게 z=histogram/std60 그대로 사용
        assert score == pytest.approx(50.0 + 50.0 * math.tanh(5.0))

    def test_missing_close_returns_none(self):
        assert _macd_score(histogram=1.0, std60=1.0, close=None) is None


class TestDowntrendGate:
    def test_long_term_downtrend_suppresses_mean_reversion_toward_center(self):
        # given: 종가가 60/120일선 모두 아래이고 60일선도 120일선 아래
        # (장기 하락추세) + RSI/%B 둘 다 극단 과매도(원점수 100)
        gated = _apply_downtrend_gate(
            mean_reversion_raw=100.0, close=50.0, ma_60=80.0, ma_120=100.0,
        )

        # then: DOWNTREND_GATE_FACTOR(0.5)만큼 중심(50) 쪽으로 당겨져
        # 100 -> 75 (편차 50 -> 25)
        assert gated == pytest.approx(75.0)

    def test_uptrend_is_not_gated(self):
        # given: 종가가 이평선 위(정배열)인 경우 게이트 미적용
        gated = _apply_downtrend_gate(
            mean_reversion_raw=100.0, close=150.0, ma_60=120.0, ma_120=100.0,
        )

        assert gated == pytest.approx(100.0)

    def test_missing_moving_averages_skip_gate(self):
        # given: 신규상장 등으로 ma_120 계산 불가 - 판단 근거가 없어 게이트 미적용
        gated = _apply_downtrend_gate(
            mean_reversion_raw=100.0, close=50.0, ma_60=80.0, ma_120=None,
        )

        assert gated == pytest.approx(100.0)

    def test_calculate_score_applies_gate_end_to_end(self):
        # given: 장기 하락추세 + 평균회귀 원점수 100(RSI/%B 극단 과매도)
        latest = _base_latest(
            close=50.0, ma_5=50.0, ma_10=60.0, ma_20=70.0, ma_60=80.0, ma_120=100.0,
            rsi=10.0, bollinger_percent_b=0.0,
            macd_histogram=None, macd_histogram_std60=None,  # 추세축은 이번 검증과 무관하게 배제
        )

        # when
        result = calculate_score(latest)

        # then: 게이트 미적용이면 100점이어야 하나, 실제로는 75점으로 억제
        assert result.mean_reversion_score == pytest.approx(75.0)
