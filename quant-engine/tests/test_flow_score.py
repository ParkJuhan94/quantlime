import numpy as np
import pandas as pd

from calculator.flow_score import (
    FLOW_WARMUP_TRADING_DAYS,
    MIN_STRENGTH_STD,
    compute_flow_scores,
)

ROWS = 120


def _frame(foreign=None, institution=None, volume=None, rows=ROWS) -> pd.DataFrame:
    rng = np.random.default_rng(7)
    return pd.DataFrame(
        {
            "date": pd.bdate_range("2025-03-05", periods=rows),
            "volume": volume if volume is not None else np.full(rows, 1000.0),
            "foreign_net": foreign if foreign is not None else rng.normal(0, 50, rows),
            "institution_net": institution if institution is not None else rng.normal(0, 50, rows),
            "pension_net": np.zeros(rows),
            "trust_net": np.zeros(rows),
        }
    )


class TestComputeFlowScores:
    def test_sustained_buying_scores_above_neutral(self):
        df = _frame(foreign=np.full(ROWS, 100.0), institution=np.full(ROWS, 100.0))
        score = compute_flow_scores(df)["smart_money"]
        assert score.iloc[-1] > 50

    def test_sustained_selling_scores_below_neutral(self):
        df = _frame(foreign=np.full(ROWS, -100.0), institution=np.full(ROWS, -100.0))
        score = compute_flow_scores(df)["smart_money"]
        assert score.iloc[-1] < 50

    def test_scores_stay_within_0_100(self):
        df = _frame()
        scores = compute_flow_scores(df)["smart_money"].dropna()
        assert not scores.empty
        assert scores.between(0, 100).all()

    def test_warmup_rows_are_missing(self):
        scores = compute_flow_scores(_frame())["smart_money"]
        assert scores.iloc[: FLOW_WARMUP_TRADING_DAYS - 1].isna().all()
        assert scores.iloc[FLOW_WARMUP_TRADING_DAYS - 1 :].notna().all()

    def test_missing_net_buy_blanks_scores_within_window(self):
        foreign = np.random.default_rng(1).normal(0, 50, ROWS)
        foreign[100] = np.nan
        scores = compute_flow_scores(_frame(foreign=foreign))["smart_money"]
        assert np.isnan(scores.iloc[100])
        assert scores.iloc[99:100].notna().all()

    def test_zero_volume_day_blanks_score(self):
        volume = np.full(ROWS, 1000.0)
        volume[100] = 0.0
        scores = compute_flow_scores(_frame(volume=volume))["smart_money"]
        assert np.isnan(scores.iloc[100])

    def test_std_floor_prevents_saturation_for_tiny_flows(self):
        # 거래량 대비 0.001%의 미세한 순매수: 하한이 없으면 std가 작아 100점으로 포화한다
        tiny = np.full(ROWS, 0.01)
        df = _frame(foreign=tiny, institution=tiny, volume=np.full(ROWS, 1000.0))
        strength_only = compute_flow_scores(df)["smart_money"].iloc[-1]
        # 지속성 100 + 강도 약 50 -> 평균 약 75, 강도가 포화했다면 100에 가깝다
        assert strength_only < 80
        assert MIN_STRENGTH_STD > 0

    def test_no_lookahead(self):
        df = _frame()
        base = compute_flow_scores(df)["smart_money"]
        changed = df.copy()
        changed.loc[100:, ["foreign_net", "institution_net", "volume"]] = [9999.0, 9999.0, 5.0]
        after = compute_flow_scores(changed)["smart_money"]
        pd.testing.assert_series_equal(base.iloc[:100], after.iloc[:100])

    def test_unsorted_input_is_sorted_by_date(self):
        df = _frame()
        shuffled = df.sample(frac=1, random_state=3)
        pd.testing.assert_frame_equal(compute_flow_scores(df), compute_flow_scores(shuffled))

    def test_candidates_use_their_own_columns(self):
        df = _frame()
        df["pension_net"] = 100.0
        scores = compute_flow_scores(df)
        assert scores["pension_fund"].iloc[-1] > 50
        assert scores["investment_trust"].iloc[-1] < 100
