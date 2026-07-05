import math

import pytest

from calculator.scorer import DIVERGENCE_THRESHOLD, calculate_score


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
        assert result.grade == "B"
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

    def test_all_trend_indicators_missing_falls_back_to_mean_reversion_only(self):
        # given: 추세추종 축 전체 데이터 부족
        latest = _base_latest(
            macd_histogram=None,
            macd_histogram_std60=None,
            ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None,
            rsi=20.0,
        )

        # when
        result = calculate_score(latest)

        # then
        assert result.trend_score is None
        assert result.mean_reversion_score is not None
        assert result.composite_score == result.mean_reversion_score
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


class TestGradeCutoffs:
    @pytest.mark.parametrize(
        "composite_inputs,expected_grade",
        [
            # 두 축 모두 최고점(추세=MACD 강한 양수, 평균회귀=과매도 극단)
            ({"rsi": 15.0, "bollinger_percent_b": 0.0, "macd_histogram": 10.0}, "SSS"),
            # 두 축 모두 정확히 중립(50)
            ({"rsi": 50.0, "bollinger_percent_b": 0.5, "macd_histogram": 0.0}, "B"),
        ],
    )
    def test_grade_matches_expected_tier(self, composite_inputs, expected_grade):
        # given: MA는 이산값이라 정확한 경계 테스트에서 제외
        latest = _base_latest(
            ma_5=None, ma_10=None, ma_20=None, ma_60=None, ma_120=None,
            **composite_inputs,
        )

        # when
        result = calculate_score(latest)

        # then
        assert result.grade == expected_grade
