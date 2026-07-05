"""기술적 지표 계산.

모든 함수는 날짜 오름차순으로 정렬된 pandas Series/DataFrame을 받아
지표의 "원값(raw value)"을 반환한다. 점수 변환은 scorer.py의 책임이다.
데이터가 부족해 계산할 수 없는 구간은 NaN으로 남긴다 (콜드스타트 처리는
scorer.py에서 NaN 여부로 판단).
"""

from __future__ import annotations

import numpy as np
import pandas as pd

MA_PERIODS: list[int] = [5, 10, 20, 60, 120]

RSI_PERIOD = 14
MACD_FAST, MACD_SLOW, MACD_SIGNAL = 12, 26, 9
MACD_HISTOGRAM_STD_WINDOW = 60
BOLLINGER_PERIOD = 20
BOLLINGER_NUM_STD = 2.0
VOLUME_RATIO_PERIOD = 20

# 지표별 계산에 필요한 최소 거래일수. 콜드스타트(데이터 부족) 판단에 사용.
MIN_REQUIRED_DAYS: dict[str, int] = {
    "rsi": RSI_PERIOD + 1,
    "macd": MACD_SLOW + MACD_SIGNAL,
    "bollinger": BOLLINGER_PERIOD,
    "volume_ratio": VOLUME_RATIO_PERIOD + 1,  # shift(1)만큼 하루 더 필요
    **{f"ma_{p}": p for p in MA_PERIODS},
}


def compute_rsi(close: pd.Series, period: int = RSI_PERIOD) -> pd.Series:
    """RSI(period). Wilder 방식(지수이동평균 기반)."""
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)

    avg_gain = gain.ewm(alpha=1 / period, min_periods=period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1 / period, min_periods=period, adjust=False).mean()

    rs = avg_gain / avg_loss.replace(0, np.nan)
    rsi = 100 - (100 / (1 + rs))
    # 손실이 전혀 없으면 RSI는 100 (avg_loss=0 -> rs가 NaN이 되는 것 보정)
    rsi = rsi.where(avg_loss != 0, 100.0)
    return rsi


def compute_macd_histogram(
    close: pd.Series,
    fast: int = MACD_FAST,
    slow: int = MACD_SLOW,
    signal: int = MACD_SIGNAL,
) -> pd.Series:
    """MACD 히스토그램(MACD선 - 시그널선)."""
    ema_fast = close.ewm(span=fast, adjust=False, min_periods=fast).mean()
    ema_slow = close.ewm(span=slow, adjust=False, min_periods=slow).mean()
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal, adjust=False, min_periods=signal).mean()
    return macd_line - signal_line


def compute_bollinger_percent_b(
    close: pd.Series,
    period: int = BOLLINGER_PERIOD,
    num_std: float = BOLLINGER_NUM_STD,
) -> pd.Series:
    """%B = (종가 - 하단밴드) / (상단밴드 - 하단밴드)."""
    sma = close.rolling(window=period, min_periods=period).mean()
    std = close.rolling(window=period, min_periods=period).std()
    upper = sma + num_std * std
    lower = sma - num_std * std
    band_width = (upper - lower).replace(0, np.nan)
    return (close - lower) / band_width


def compute_moving_averages(
    close: pd.Series, periods: list[int] = MA_PERIODS
) -> dict[int, pd.Series]:
    """기간별 단순이동평균. {5: series, 10: series, ...}."""
    return {p: close.rolling(window=p, min_periods=p).mean() for p in periods}


def compute_volume_ratio(volume: pd.Series, period: int = VOLUME_RATIO_PERIOD) -> pd.Series:
    """당일 거래량 / 전일까지의 N일 평균 거래량.

    당일 거래량을 자기 자신의 평균에 포함시키면 급증분이 희석되어 비율이
    둔화되므로, 평균은 전일까지의 값만 사용한다(shift(1)).
    """
    avg_volume = volume.rolling(window=period, min_periods=period).mean().shift(1)
    return volume / avg_volume.replace(0, np.nan)


def compute_all_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """OHLCV DataFrame(컬럼: open, high, low, close, volume, 날짜 오름차순)에
    모든 지표 컬럼을 추가해 반환한다.
    """
    result = df.copy()
    close = result["close"]

    result["rsi"] = compute_rsi(close)
    result["macd_histogram"] = compute_macd_histogram(close)
    result["macd_histogram_std60"] = result["macd_histogram"].rolling(
        window=MACD_HISTOGRAM_STD_WINDOW, min_periods=MACD_HISTOGRAM_STD_WINDOW
    ).std()
    result["bollinger_percent_b"] = compute_bollinger_percent_b(close)
    result["volume_ratio"] = compute_volume_ratio(result["volume"])

    for period, ma_series in compute_moving_averages(close).items():
        result[f"ma_{period}"] = ma_series

    return result
