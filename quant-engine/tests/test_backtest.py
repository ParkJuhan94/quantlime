import numpy as np
import pandas as pd
import pytest

from calculator.backtest import (
    HORIZONS,
    _forward_return,
    _prepare_backtest_frame,
    _quantile_buckets,
    _rank_ic_with_bootstrap,
    _spearman,
    run_backtest,
)


class TestPrepareBacktestFrame:
    def test_inner_joins_on_date_and_drops_mismatched_rows(self):
        # given: 벤치마크에 2026-01-02 데이터가 없음(공휴일 등)
        scores_df = pd.DataFrame({
            "date": ["2026-01-01", "2026-01-02", "2026-01-03"],
            "close": [100.0, 101.0, 102.0],
            "trend_score": [50.0, 60.0, 70.0],
        })
        benchmark_df = pd.DataFrame({
            "date": ["2026-01-01", "2026-01-03"],
            "close": [1000.0, 1010.0],
        })

        # when
        merged = _prepare_backtest_frame(scores_df, benchmark_df)

        # then
        assert list(merged["date"]) == ["2026-01-01", "2026-01-03"]
        assert "benchmark_close" in merged.columns


class TestForwardReturn:
    def test_computes_percentage_change_over_horizon(self):
        close = pd.Series([100.0, 105.0, 110.0, 121.0])
        assert _forward_return(close, entry_idx=0, horizon=2) == pytest.approx(0.10)

    def test_returns_none_when_horizon_exceeds_series_length(self):
        close = pd.Series([100.0, 105.0])
        assert _forward_return(close, entry_idx=0, horizon=5) is None

    def test_returns_none_when_entry_price_is_zero(self):
        close = pd.Series([0.0, 105.0])
        assert _forward_return(close, entry_idx=0, horizon=1) is None


class TestSpearman:
    def test_perfect_monotonic_relationship_yields_ic_one(self):
        df = pd.DataFrame({"score": [1, 2, 3, 4, 5], "excess_return": [0.01, 0.02, 0.03, 0.04, 0.05]})
        assert _spearman(df) == pytest.approx(1.0)

    def test_inverse_relationship_yields_negative_ic(self):
        df = pd.DataFrame({"score": [1, 2, 3, 4, 5], "excess_return": [0.05, 0.04, 0.03, 0.02, 0.01]})
        assert _spearman(df) == pytest.approx(-1.0)

    def test_returns_none_when_score_has_no_variance(self):
        # given: 모든 점수가 동일(분산 0) - 상관계수 정의 불가
        df = pd.DataFrame({"score": [50.0, 50.0, 50.0], "excess_return": [0.01, 0.02, 0.03]})
        assert _spearman(df) is None


class TestQuantileBuckets:
    def test_splits_into_five_buckets_ordered_by_score(self):
        scores = np.arange(100)
        returns = scores * 0.001  # 점수가 높을수록 수익률도 높은 단조 구성

        buckets = _quantile_buckets(pd.DataFrame({"score": scores, "excess_return": returns}))

        assert [b.bucket for b in buckets] == [1, 2, 3, 4, 5]
        means = [b.mean_excess_return for b in buckets]
        assert means == sorted(means)
        assert sum(b.sample_size for b in buckets) == 100

    def test_returns_empty_list_when_scores_have_no_variance(self):
        # given: qcut이 분산 없는 값을 5분위로 나눌 수 없어 ValueError를 던지는 경우
        df = pd.DataFrame({"score": [50.0] * 20, "excess_return": np.linspace(-0.01, 0.01, 20)})
        assert _quantile_buckets(df) == []

    def test_returns_empty_list_for_empty_input(self):
        assert _quantile_buckets(pd.DataFrame(columns=["score", "excess_return"])) == []


class TestRankIcBootstrap:
    def test_small_sample_skips_confidence_interval(self):
        # given: BLOCK_SIZE(20)*2보다 훨씬 적은 샘플
        df = pd.DataFrame({"score": [1, 2, 3], "excess_return": [0.01, 0.02, 0.03]})

        ic, low, high = _rank_ic_with_bootstrap(df)

        assert ic == pytest.approx(1.0)
        assert low is None
        assert high is None

    def test_large_sample_yields_confidence_interval_around_ic(self):
        rng = np.random.default_rng(0)
        n = 200
        scores = rng.normal(size=n)
        returns = scores * 0.01 + rng.normal(scale=0.001, size=n)
        df = pd.DataFrame({"score": scores, "excess_return": returns})

        ic, low, high = _rank_ic_with_bootstrap(df)

        assert ic > 0.5
        assert low is not None and high is not None
        assert low <= ic <= high


class TestRunBacktestIntegration:
    def test_runs_end_to_end_with_synthetic_data_and_returns_expected_shape(self):
        n = 300
        dates = pd.date_range("2026-01-01", periods=n, freq="B").strftime("%Y-%m-%d")
        rng = np.random.default_rng(1)

        scores_df = pd.DataFrame({
            "date": dates,
            "close": 100 + np.cumsum(rng.normal(0, 1, size=n)),
            "trend_score": rng.uniform(0, 100, size=n),
            "mean_reversion_score": rng.uniform(0, 100, size=n),
            "grade": rng.choice(["STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL"], size=n),
        })
        benchmark_df = pd.DataFrame({
            "date": dates,
            "close": 1000 + np.cumsum(rng.normal(0, 5, size=n)),
        })

        results = run_backtest(scores_df, benchmark_df)

        assert {r.axis for r in results} == {"trend", "mean_reversion"}
        for axis_result in results:
            assert [h.horizon for h in axis_result.horizons] == HORIZONS
            for h in axis_result.horizons:
                assert h.sample_size > 0
            assert axis_result.stability.score_autocorrelation is not None
