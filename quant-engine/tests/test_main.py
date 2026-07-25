from datetime import date, timedelta

from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def _make_ohlcv(days: int, start_price: float = 100.0, trend: float = 0.5) -> list[dict]:
    start_date = date(2026, 1, 1)
    ohlcv = []
    price = start_price
    for i in range(days):
        price += trend
        ohlcv.append({
            "date": (start_date + timedelta(days=i)).isoformat(),
            "open": price,
            "high": price + 1,
            "low": price - 1,
            "close": price,
            "volume": 1000.0,
        })
    return ohlcv


class TestHealth:
    def test_health_returns_ok(self):
        response = client.get("/health")

        assert response.status_code == 200
        assert response.json() == {"status": "ok"}


class TestCalculateScoreSeries:
    def test_sufficient_data_returns_full_series(self):
        # given: 130거래일치 상승 추세 데이터 (모든 지표 계산 가능)
        request_body = {
            "stocks": [
                {"stock_code": "005930", "ohlcv": _make_ohlcv(days=130)},
            ]
        }

        # when
        response = client.post("/calculate/score/series", json=request_body)

        # then: 날짜별 스코어 시계열이 OHLCV 건수만큼 통째로 내려온다
        assert response.status_code == 200
        stocks = response.json()["scores"]
        assert len(stocks) == 1
        stock = stocks[0]
        assert stock["stock_code"] == "005930"
        daily_scores = stock["daily_scores"]
        assert len(daily_scores) == 130

        latest = daily_scores[-1]
        assert latest["insufficient_data"] is False
        assert latest["trend_score"] is not None
        assert latest["mean_reversion_score"] is not None
        assert latest["composite_score"] is not None
        assert latest["grade"] in {"STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL"}
        assert latest["quadrant"] in {
            "trend_up_oversold", "trend_up_overbought",
            "trend_down_oversold", "trend_down_overbought",
        }

        earliest = daily_scores[0]
        assert earliest["insufficient_data"] is True
        assert earliest["trend_score"] is None
        assert earliest["mean_reversion_score"] is None
        assert earliest["quadrant"] is None

    def test_insufficient_data_marks_flag_for_short_history(self):
        # given: 3거래일치 데이터만 존재 (신규상장 직후 상황)
        request_body = {
            "stocks": [
                {"stock_code": "999999", "ohlcv": _make_ohlcv(days=3)},
            ]
        }

        # when
        response = client.post("/calculate/score/series", json=request_body)

        # then
        assert response.status_code == 200
        daily_scores = response.json()["scores"][0]["daily_scores"]
        assert len(daily_scores) == 3
        assert all(row["insufficient_data"] is True for row in daily_scores)
        assert all(row["trend_score"] is None for row in daily_scores)
        assert all(row["mean_reversion_score"] is None for row in daily_scores)
        assert all(row["quadrant"] is None for row in daily_scores)

    def test_multiple_stocks_in_one_batch(self):
        # given
        request_body = {
            "stocks": [
                {"stock_code": "005930", "ohlcv": _make_ohlcv(days=130, trend=0.5)},
                {"stock_code": "000660", "ohlcv": _make_ohlcv(days=130, trend=-0.5)},
            ]
        }

        # when
        response = client.post("/calculate/score/series", json=request_body)

        # then
        assert response.status_code == 200
        stocks = response.json()["scores"]
        assert [s["stock_code"] for s in stocks] == ["005930", "000660"]


class TestBacktestScore:
    def test_returns_both_axes_with_all_horizons(self):
        # given: 벤치마크보다 완만하게 오르는 300거래일치 데이터(초과수익률이
        # 발생하도록 종목 추세를 벤치마크보다 강하게 설정)
        request_body = {
            "stock_code": "005930",
            "ohlcv": _make_ohlcv(days=300, trend=0.5),
            "benchmark_ohlcv": _make_ohlcv(days=300, trend=0.1),
        }

        # when
        response = client.post("/backtest/score", json=request_body)

        # then
        assert response.status_code == 200
        body = response.json()
        assert body["stock_code"] == "005930"
        assert body["score_version"]
        assert body["sample_days"] == 300
        assert {axis["axis"] for axis in body["axes"]} == {"trend", "mean_reversion"}
        for axis in body["axes"]:
            assert [h["horizon"] for h in axis["horizons"]] == [5, 10, 20, 60]
            for horizon in axis["horizons"]:
                assert horizon["sample_size"] >= 0

    def test_includes_daily_score_series_with_none_during_warmup(self):
        # given: 초반 워밍업 구간(120일 이평 등 아직 계산 불가)이 포함된 데이터
        request_body = {
            "stock_code": "005930",
            "ohlcv": _make_ohlcv(days=300, trend=0.5),
            "benchmark_ohlcv": _make_ohlcv(days=300, trend=0.1),
        }

        # when
        response = client.post("/backtest/score", json=request_body)

        # then: 프론트 차트 오버레이/히스토그램용 일별 시계열이 함께 내려오고,
        # NaN이 유효하지 않은 JSON이라 워밍업 구간은 null로 정상 변환돼야 한다
        body = response.json()
        daily_scores = body["daily_scores"]
        assert len(daily_scores) == 300
        assert daily_scores[0]["trend_score"] is None
        assert daily_scores[0]["quadrant"] is None
        assert daily_scores[-1]["trend_score"] is not None
        assert daily_scores[-1]["close"] > 0
