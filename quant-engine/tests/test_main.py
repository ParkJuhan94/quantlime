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


class TestCalculateScoreBatch:
    def test_sufficient_data_returns_full_scores(self):
        # given: 130거래일치 상승 추세 데이터 (모든 지표 계산 가능)
        request_body = {
            "stocks": [
                {"stock_code": "005930", "ohlcv": _make_ohlcv(days=130)},
            ]
        }

        # when
        response = client.post("/calculate/score/batch", json=request_body)

        # then
        assert response.status_code == 200
        scores = response.json()["scores"]
        assert len(scores) == 1
        score = scores[0]
        assert score["stock_code"] == "005930"
        assert score["insufficient_data"] is False
        assert score["trend_score"] is not None
        assert score["mean_reversion_score"] is not None
        assert score["composite_score"] is not None
        assert score["grade"] in {"STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL"}
        assert score["quadrant"] in {
            "trend_up_oversold", "trend_up_overbought",
            "trend_down_oversold", "trend_down_overbought",
        }
        assert isinstance(score["comment"], str) and len(score["comment"]) > 0

    def test_insufficient_data_marks_flag(self):
        # given: 3거래일치 데이터만 존재 (신규상장 직후 상황)
        request_body = {
            "stocks": [
                {"stock_code": "999999", "ohlcv": _make_ohlcv(days=3)},
            ]
        }

        # when
        response = client.post("/calculate/score/batch", json=request_body)

        # then
        assert response.status_code == 200
        score = response.json()["scores"][0]
        assert score["insufficient_data"] is True
        assert score["trend_score"] is None
        assert score["mean_reversion_score"] is None
        assert score["quadrant"] is None
        assert score["comment"] == "데이터가 부족해 코멘트를 생성할 수 없습니다."

    def test_multiple_stocks_in_one_batch(self):
        # given
        request_body = {
            "stocks": [
                {"stock_code": "005930", "ohlcv": _make_ohlcv(days=130, trend=0.5)},
                {"stock_code": "000660", "ohlcv": _make_ohlcv(days=130, trend=-0.5)},
            ]
        }

        # when
        response = client.post("/calculate/score/batch", json=request_body)

        # then
        assert response.status_code == 200
        scores = response.json()["scores"]
        assert [s["stock_code"] for s in scores] == ["005930", "000660"]


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
