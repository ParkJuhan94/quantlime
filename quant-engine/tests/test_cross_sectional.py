import numpy as np
import pandas as pd
import pytest

from calculator.cross_sectional import (
    MIN_STOCKS_PER_DATE,
    _bootstrap_daily_ic,
    _circular_shift_panel,
    _cross_sectional_buckets,
    _daily_cross_sectional_ic,
    build_panel,
    run_cross_sectional_backtest,
)


def _stock_scores_df(seed: int, n: int, start: str = "2026-01-01") -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    dates = pd.date_range(start, periods=n, freq="B").strftime("%Y-%m-%d")
    return pd.DataFrame({
        "date": dates,
        "close": 100 + np.cumsum(rng.normal(0, 1, size=n)),
        "trend_score": rng.uniform(0, 100, size=n),
        "mean_reversion_score": rng.uniform(0, 100, size=n),
    })


def _benchmark_df(n: int, start: str = "2026-01-01") -> pd.DataFrame:
    rng = np.random.default_rng(999)
    dates = pd.date_range(start, periods=n, freq="B").strftime("%Y-%m-%d")
    return pd.DataFrame({"date": dates, "close": 1000 + np.cumsum(rng.normal(0, 5, size=n))})


class TestBuildPanel:
    def test_combines_multiple_stocks_into_one_panel_with_stock_code(self):
        n = 200
        stocks = [(f"00{i}", _stock_scores_df(i, n)) for i in range(3)]
        benchmark_df = _benchmark_df(n)

        panel = build_panel(stocks, benchmark_df, axis="trend", horizon=5)

        assert set(panel["stock_code"].unique()) == {"000", "001", "002"}
        assert {"date", "score", "excess_return", "stock_code"} <= set(panel.columns)

    def test_excludes_warmup_rows_from_panel(self):
        # given: 워밍업(추세추종=93거래일) 미만 구간은 어떤 종목도 패널에
        # 들어가지 않아야 한다
        n = 200
        stocks = [("000660", _stock_scores_df(1, n))]
        benchmark_df = _benchmark_df(n)

        panel = build_panel(stocks, benchmark_df, axis="trend", horizon=5)

        assert panel["date"].min() > _benchmark_df(n)["date"].iloc[92]


class TestDailyCrossSectionalIc:
    def test_below_min_stocks_per_date_is_excluded(self):
        dates = ["2026-01-01"] * 5
        panel = pd.DataFrame({
            "date": dates,
            "score": [1, 2, 3, 4, 5],
            "excess_return": [0.01, 0.02, 0.03, 0.04, 0.05],
            "stock_code": ["a", "b", "c", "d", "e"],
        })

        result = _daily_cross_sectional_ic(panel)

        assert result.empty

    def test_perfect_monotonic_relationship_yields_ic_near_one(self):
        n_stocks = MIN_STOCKS_PER_DATE
        dates = ["2026-01-01"] * n_stocks
        panel = pd.DataFrame({
            "date": dates,
            "score": list(range(n_stocks)),
            "excess_return": [i * 0.001 for i in range(n_stocks)],
            "stock_code": [f"s{i}" for i in range(n_stocks)],
        })

        result = _daily_cross_sectional_ic(panel)

        assert result.iloc[0] == pytest.approx(1.0)

    def test_averages_across_multiple_dates(self):
        n_stocks = MIN_STOCKS_PER_DATE
        frames = []
        for date in ["2026-01-01", "2026-01-02"]:
            frames.append(pd.DataFrame({
                "date": [date] * n_stocks,
                "score": list(range(n_stocks)),
                "excess_return": [i * 0.001 for i in range(n_stocks)],
                "stock_code": [f"s{i}" for i in range(n_stocks)],
            }))
        panel = pd.concat(frames, ignore_index=True)

        result = _daily_cross_sectional_ic(panel)

        assert len(result) == 2
        assert all(v == pytest.approx(1.0) for v in result)


class TestBootstrapDailyIc:
    def test_below_block_threshold_skips_confidence_interval(self):
        daily_ic = pd.Series(np.linspace(-0.1, 0.1, 10))
        low, high = _bootstrap_daily_ic(daily_ic, block_size=20)
        assert low is None and high is None

    def test_large_sample_yields_confidence_interval(self):
        rng = np.random.default_rng(0)
        daily_ic = pd.Series(rng.normal(0.05, 0.02, size=200))
        low, high = _bootstrap_daily_ic(daily_ic, block_size=20, rng=np.random.default_rng(1))
        assert low is not None and high is not None
        assert low <= daily_ic.mean() <= high


class TestCrossSectionalBuckets:
    def test_splits_each_date_into_five_buckets_and_aggregates(self):
        n_stocks = 20
        frames = []
        for date in ["2026-01-01", "2026-01-02"]:
            frames.append(pd.DataFrame({
                "date": [date] * n_stocks,
                "score": list(range(n_stocks)),
                "excess_return": [i * 0.001 for i in range(n_stocks)],
                "stock_code": [f"s{i}" for i in range(n_stocks)],
            }))
        panel = pd.concat(frames, ignore_index=True)

        buckets = _cross_sectional_buckets(panel)

        assert [b.bucket for b in buckets] == [1, 2, 3, 4, 5]
        means = [b.mean_excess_return for b in buckets]
        assert means == sorted(means)
        assert sum(b.sample_size for b in buckets) == n_stocks * 2

    def test_returns_empty_for_empty_panel(self):
        assert _cross_sectional_buckets(pd.DataFrame(columns=["date", "score", "excess_return", "stock_code"])) == []


class TestCircularShiftPanel:
    def test_preserves_score_multiset_per_stock(self):
        panel = pd.DataFrame({
            "date": ["2026-01-01", "2026-01-02", "2026-01-03"] * 2,
            "score": [10.0, 20.0, 30.0, 100.0, 200.0, 300.0],
            "excess_return": [0.01, 0.02, 0.03, 0.04, 0.05, 0.06],
            "stock_code": ["a", "a", "a", "b", "b", "b"],
        })
        rng = np.random.default_rng(0)

        shifted = _circular_shift_panel(panel, rng)

        for code in ["a", "b"]:
            original = sorted(panel[panel["stock_code"] == code]["score"])
            after = sorted(shifted[shifted["stock_code"] == code]["score"])
            assert original == after

    def test_actually_changes_alignment_with_high_probability(self):
        # given: 회전 오프셋이 1..n-1 범위라 n>=2면 최소 한 번은 원본과
        # 달라야 한다(순열 정체성 방지)
        panel = pd.DataFrame({
            "date": [f"2026-01-{d:02d}" for d in range(1, 11)],
            "score": list(range(10)),
            "excess_return": [i * 0.001 for i in range(10)],
            "stock_code": ["a"] * 10,
        })
        rng = np.random.default_rng(0)

        shifted = _circular_shift_panel(panel, rng)

        assert not (shifted.sort_values("date")["score"].to_numpy() == panel["score"].to_numpy()).all()


class TestRunCrossSectionalBacktest:
    def test_runs_end_to_end_for_one_axis_and_horizon(self):
        n = 200
        stocks = [(f"{i:03d}", _stock_scores_df(i, n)) for i in range(25)]
        benchmark_df = _benchmark_df(n)

        stat = run_cross_sectional_backtest(stocks, benchmark_df, axis="trend", horizon=20)

        assert stat.horizon == 20
        assert stat.n_observations >= 0
        assert stat.null_mean is None  # null_test=False 기본값

    def test_null_test_populates_null_distribution_stats(self):
        n = 200
        stocks = [(f"{i:03d}", _stock_scores_df(i, n)) for i in range(25)]
        benchmark_df = _benchmark_df(n)

        stat = run_cross_sectional_backtest(
            stocks, benchmark_df, axis="mean_reversion", horizon=5, null_test=True, null_repeats=10,
        )

        if stat.mean_ic is not None:
            assert stat.null_mean is not None
            assert stat.null_p2_5 is not None and stat.null_p97_5 is not None
            assert stat.null_p2_5 <= stat.null_p97_5
