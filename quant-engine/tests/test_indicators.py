import numpy as np
import pandas as pd
import pytest

from calculator.indicators import (
    compute_all_indicators,
    compute_bollinger_percent_b,
    compute_macd_histogram,
    compute_moving_averages,
    compute_rsi,
    compute_volume_ratio,
)


def _make_price_series(prices: list[float]) -> pd.Series:
    return pd.Series(prices, dtype=float)


class TestComputeRsi:
    def test_all_gains_returns_100(self):
        # given: 15일 연속 상승
        close = _make_price_series([100 + i for i in range(15)])

        # when
        rsi = compute_rsi(close)

        # then
        assert rsi.iloc[-1] == pytest.approx(100.0)

    def test_all_losses_returns_0(self):
        # given: 15일 연속 하락
        close = _make_price_series([100 - i for i in range(15)])

        # when
        rsi = compute_rsi(close)

        # then
        assert rsi.iloc[-1] == pytest.approx(0.0)

    def test_insufficient_data_returns_nan(self):
        # given: RSI(14) 계산에 필요한 기간보다 짧은 데이터
        close = _make_price_series([100, 101, 102])

        # when
        rsi = compute_rsi(close)

        # then
        assert np.isnan(rsi.iloc[-1])


class TestComputeMacdHistogram:
    def test_insufficient_data_returns_nan(self):
        # given: MACD(12,26,9) 계산에 필요한 35일보다 짧은 데이터
        close = _make_price_series([100 + i for i in range(20)])

        # when
        histogram = compute_macd_histogram(close)

        # then
        assert np.isnan(histogram.iloc[-1])

    def test_uptrend_produces_positive_histogram(self):
        # given: 충분한 기간의 꾸준한 상승 추세
        close = _make_price_series([100 + i * 2 for i in range(60)])

        # when
        histogram = compute_macd_histogram(close)

        # then: 상승 추세에서는 MACD선이 시그널선 위에 위치(히스토그램 양수)
        assert histogram.iloc[-1] > 0


class TestComputeBollingerPercentB:
    def test_flat_price_percent_b_is_nan_due_to_zero_bandwidth(self):
        # given: 변동 없는 가격 (표준편차 0 -> 밴드폭 0)
        close = _make_price_series([100.0] * 25)

        # when
        percent_b = compute_bollinger_percent_b(close)

        # then
        assert np.isnan(percent_b.iloc[-1])

    def test_insufficient_data_returns_nan(self):
        # given
        close = _make_price_series([100, 101, 102])

        # when
        percent_b = compute_bollinger_percent_b(close)

        # then
        assert np.isnan(percent_b.iloc[-1])


class TestComputeMovingAverages:
    def test_returns_nan_for_periods_exceeding_data_length(self):
        # given: 30일치 데이터 (MA60, MA120 계산 불가)
        close = _make_price_series([100 + i for i in range(30)])

        # when
        mas = compute_moving_averages(close)

        # then
        assert not np.isnan(mas[5].iloc[-1])
        assert not np.isnan(mas[20].iloc[-1])
        assert np.isnan(mas[60].iloc[-1])
        assert np.isnan(mas[120].iloc[-1])


class TestComputeVolumeRatio:
    def test_double_average_volume_returns_ratio_2(self):
        # given: 20일 평균 거래량 대비 오늘 거래량이 2배
        volume = _make_price_series([1000.0] * 20 + [2000.0])

        # when
        ratio = compute_volume_ratio(volume)

        # then
        assert ratio.iloc[-1] == pytest.approx(2.0)


class TestComputeAllIndicators:
    def test_adds_all_expected_columns(self):
        # given
        df = pd.DataFrame({
            "close": [100 + i for i in range(130)],
            "volume": [1000.0] * 130,
        })

        # when
        result = compute_all_indicators(df)

        # then
        expected_columns = {
            "rsi", "macd_histogram", "macd_histogram_std60",
            "bollinger_percent_b", "volume_ratio",
            "ma_5", "ma_10", "ma_20", "ma_60", "ma_120",
        }
        assert expected_columns.issubset(result.columns)
        # 130일치 데이터가 있으므로 마지막 행은 모든 지표가 계산 가능해야 함
        last_row = result.iloc[-1]
        for column in expected_columns:
            assert not np.isnan(last_row[column]), f"{column} should not be NaN"
