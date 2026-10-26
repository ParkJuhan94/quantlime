"""수급 점수(`calculator/flow_score.py`) 횡단면 검증 스크립트 (연구용, 서비스 경로 아님).

기존 `calculator/cross_sectional.py`와 같은 방법론(날짜별 Spearman IC → 날짜 평균, 날짜
블록 부트스트랩 신뢰구간, 종목별 순환이동 null 200회, 신호는 T 종가 이후 T+1 종가 진입)을
쓰되, 8만 번 이상 반복해야 하는 null 계산 때문에 (날짜 x 종목) 행렬로 벡터화했다.
`--selfcheck`가 forward 초과수익률 계산이 `backtest._excess_returns_for_horizon`과 같은지
확인한다. 엔진에는 DB 드라이버가 없어 입력은 CSV다(아래 사용법 참고).

사용법(CSV는 mysql -B 출력의 탭을 쉼표로 바꾼 것):
    flow.csv   stock_code,trade_date,foreign_net,institution_net,individual_net,pension_net,trust_net
               (개인 순매수가 null이 아닌 확정 행만)
    price.csv  stock_code,trade_date,open_price,high_price,low_price,close_price,volume
    bench.csv  index_code,trade_date,close   (KOSPI, KOSDAQ)
    stock.csv  stock_code,market_type        (KOSPI, KOSDAQ)

    python -I research/flow_score_study.py --data <csv 폴더> --out <결과 폴더> [--repeats 200]

유니버스는 그날 기준 point-in-time 유동성 규칙이다: 직전 20거래일 평균 거래대금(close x
volume) >= 10억원 AND 그 구간 거래량 0일 < 5 (`StockLiquidityService`의 국내 규칙과 동일,
`stock_liquidity` 테이블은 최신 스냅샷뿐이라 과거 날짜에 쓰면 생존 편향이 생겨 쓰지 않는다).
"""

from __future__ import annotations

import argparse
import json
import sys
from multiprocessing import Pool
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from calculator.backtest import (  # noqa: E402
    _excess_returns_for_horizon,
    _prepare_backtest_frame,
)
from calculator.cross_sectional import (  # noqa: E402
    MIN_STOCKS_PER_DATE,
    _bootstrap_daily_ic,
    _cross_sectional_buckets,
)
from calculator.flow_score import FLOW_CANDIDATES  # noqa: E402
from calculator.indicators import compute_all_indicators  # noqa: E402
from calculator.scorer import compute_scores  # noqa: E402

JUDGE_HORIZONS = (1, 5, 10, 20)
REFERENCE_HORIZONS = (60,)
ALL_HORIZONS = JUDGE_HORIZONS + REFERENCE_HORIZONS
MARKETS = ("KOSPI", "KOSDAQ")
PRIMARY = "smart_money"
BASELINES = ("composite", "mean_reversion", "trend")
MIN_AVG_TRADING_VALUE = 1_000_000_000
MAX_ZERO_VOLUME_DAYS = 5
LIQUIDITY_WINDOW = 20
TREND_WARMUP, MEAN_REVERSION_WARMUP = 93, 20  # calculator.backtest.WARMUP_TRADING_DAYS
SEED = 20261027

_BENCH: dict[str, pd.DataFrame] = {}


def _init_worker(bench: dict[str, pd.DataFrame]) -> None:
    _BENCH.update(bench)


def _forward_excess(merged: pd.DataFrame, horizon: int) -> pd.Series:
    """T+1 종가 진입, horizon 거래일 뒤 청산한 종목 - 벤치마크 초과수익률(행 T 기준)."""
    entry = merged["close"].shift(-1)
    exit_ = merged["close"].shift(-1 - horizon)
    bench_entry = merged["benchmark_close"].shift(-1)
    bench_exit = merged["benchmark_close"].shift(-1 - horizon)
    stock_ret = (exit_ / entry - 1.0).where(entry > 0)
    bench_ret = (bench_exit / bench_entry - 1.0).where(bench_entry > 0)
    return stock_ret - bench_ret


def _build_stock(args: tuple[str, str, pd.DataFrame, pd.DataFrame]) -> pd.DataFrame:
    from calculator.flow_score import compute_flow_scores

    code, market, price, flow = args
    price = price.sort_values("date").reset_index(drop=True)
    ohlcv = price.rename(
        columns={"open_price": "open", "high_price": "high", "low_price": "low", "close_price": "close"}
    )[["date", "open", "high", "low", "close", "volume"]].astype(
        {"open": float, "high": float, "low": float, "close": float, "volume": float}
    )

    scores = compute_scores(compute_all_indicators(ohlcv))
    frame = pd.DataFrame({"date": ohlcv["date"]})
    row_index = np.arange(len(frame))
    trend = pd.to_numeric(scores["trend_score"], errors="coerce").where(row_index >= TREND_WARMUP)
    mean_rev = pd.to_numeric(scores["mean_reversion_score"], errors="coerce").where(row_index >= MEAN_REVERSION_WARMUP)
    frame["trend"] = trend
    frame["mean_reversion"] = mean_rev
    frame["composite"] = pd.to_numeric(scores["composite_score"], errors="coerce").where(row_index >= TREND_WARMUP)

    trading_value = ohlcv["close"] * ohlcv["volume"]
    zero_days = (ohlcv["volume"] == 0).astype(float).rolling(LIQUIDITY_WINDOW, min_periods=LIQUIDITY_WINDOW).sum()
    avg_value = trading_value.rolling(LIQUIDITY_WINDOW, min_periods=LIQUIDITY_WINDOW).mean()
    frame["liquid"] = (avg_value >= MIN_AVG_TRADING_VALUE) & (zero_days < MAX_ZERO_VOLUME_DAYS)

    joined = ohlcv[["date", "volume"]].merge(flow, on="date", how="left")
    flow_scores = compute_flow_scores(joined)
    for name in FLOW_CANDIDATES:
        frame[name] = flow_scores[name].to_numpy()

    merged = frame.assign(close=ohlcv["close"]).merge(
        _BENCH[market].rename(columns={"close": "benchmark_close"}), on="date", how="inner"
    )
    for horizon in ALL_HORIZONS:
        merged[f"ex{horizon}"] = _forward_excess(merged, horizon)
    merged["stock_code"] = code
    merged["market"] = market
    return merged.drop(columns=["close", "benchmark_close"])


def _selfcheck(price: pd.DataFrame, bench: dict[str, pd.DataFrame], code: str, market: str) -> None:
    """벡터화한 forward 초과수익률이 backtest._excess_returns_for_horizon과 일치하는지 확인."""
    p = price[price["stock_code"] == code].sort_values("date").reset_index(drop=True)
    df = pd.DataFrame({"date": p["date"], "close": p["close_price"].astype(float), "score": 50.0})
    merged = _prepare_backtest_frame(df, bench[market])
    for horizon in (1, 5, 20):
        original = _excess_returns_for_horizon(merged, "score", horizon, 0).set_index("date")["excess_return"]
        mine = _forward_excess(merged, horizon)
        mine.index = merged["date"]
        mine = mine.dropna()
        common = original.index.intersection(mine.index)
        assert len(common) > 100 and len(common) == len(original) == len(mine), (horizon, len(common))
        assert np.allclose(original.loc[common], mine.loc[common]), horizon
    print(f"[selfcheck] {code}: forward 초과수익률이 backtest 구현과 일치")


def _row_corr(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    """행(날짜)별 Pearson 상관. 두 배열이 모두 유한한 종목이 MIN_STOCKS_PER_DATE 미만이면 NaN."""
    mask = np.isfinite(a) & np.isfinite(b)
    n = mask.sum(axis=1)
    safe_n = np.maximum(n, 1)
    a0 = np.where(mask, a, 0.0)
    b0 = np.where(mask, b, 0.0)
    ac = np.where(mask, a - (a0.sum(axis=1) / safe_n)[:, None], 0.0)
    bc = np.where(mask, b - (b0.sum(axis=1) / safe_n)[:, None], 0.0)
    cov = (ac * bc).sum(axis=1)
    va = (ac * ac).sum(axis=1)
    vb = (bc * bc).sum(axis=1)
    with np.errstate(invalid="ignore", divide="ignore"):
        corr = cov / np.sqrt(va * vb)
    corr[(n < MIN_STOCKS_PER_DATE) | (va == 0) | (vb == 0)] = np.nan
    return corr


def _rank_rows(x: np.ndarray, mask: np.ndarray) -> np.ndarray:
    return pd.DataFrame(np.where(mask, x, np.nan)).rank(axis=1).to_numpy()


def _daily_ic(score: np.ndarray, ret_rank: np.ndarray, mask: np.ndarray, control_rank: np.ndarray | None) -> pd.Series:
    """score 순위(필요하면 control 순위로 날짜별 단순회귀한 잔차)와 수익률 순위의 날짜별 IC."""
    score_rank = _rank_rows(score, mask)
    if control_rank is not None:
        m = mask
        n = np.maximum(m.sum(axis=1), 1)[:, None]
        sc = np.where(m, score_rank - np.where(m, score_rank, 0).sum(axis=1, keepdims=True) / n, 0.0)
        cc = np.where(m, control_rank - np.where(m, control_rank, 0).sum(axis=1, keepdims=True) / n, 0.0)
        with np.errstate(invalid="ignore", divide="ignore"):
            beta = (sc * cc).sum(axis=1, keepdims=True) / (cc * cc).sum(axis=1, keepdims=True)
        score_rank = np.where(m, sc - np.nan_to_num(beta) * cc, np.nan)
    return pd.Series(_row_corr(score_rank, ret_rank))


def _circular_shift(score: np.ndarray, mask: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """종목별로 유효 구간의 스코어를 무작위 오프셋만큼 순환이동(cross_sectional._circular_shift_panel과 동일)."""
    out = np.full_like(score, np.nan)
    for j in range(score.shape[1]):
        idx = np.flatnonzero(mask[:, j])
        if len(idx) < 2:
            out[idx, j] = score[idx, j]
            continue
        out[idx, j] = np.roll(score[idx, j], int(rng.integers(1, len(idx))))
    return out


def _evaluate(score, ret, control, mask, repeats, rng) -> dict:
    ret_rank = _rank_rows(ret, mask)
    control_rank = _rank_rows(control, mask) if control is not None else None
    daily = _daily_ic(score, ret_rank, mask, control_rank)
    daily_valid = daily.dropna()
    result: dict = {
        "mean_ic": float(daily_valid.mean()) if len(daily_valid) else None,
        "n_dates": int(len(daily_valid)),
        "n_observations": int(mask.sum()),
    }
    if not len(daily_valid):
        return result
    low, high = _bootstrap_daily_ic(daily_valid, rng=rng)
    result["ci_low"], result["ci_high"] = low, high
    null_means = []
    for _ in range(repeats):
        shifted = _circular_shift(score, mask, rng)
        null_daily = _daily_ic(shifted, ret_rank, mask, control_rank).dropna()
        if len(null_daily):
            null_means.append(float(null_daily.mean()))
    if null_means:
        arr = np.array(null_means)
        result["null_mean"], result["null_std"] = float(arr.mean()), float(arr.std())
        result["null_p2_5"], result["null_p97_5"] = (float(v) for v in np.percentile(arr, [2.5, 97.5]))
        ic = result["mean_ic"]
        result["verdict"] = "ABOVE" if ic > result["null_p97_5"] else "BELOW" if ic < result["null_p2_5"] else "inside"
    return result


def _buckets(score, ret, mask, dates, codes) -> list[dict]:
    rows, cols = np.nonzero(mask)
    panel = pd.DataFrame(
        {"date": dates[rows], "stock_code": codes[cols], "score": score[rows, cols], "excess_return": ret[rows, cols]}
    )
    return [vars(b) for b in _cross_sectional_buckets(panel)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--repeats", type=int, default=200)
    parser.add_argument("--limit-stocks", type=int, default=0, help="개발용: 시장별 종목 수 제한")
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()
    data, out = Path(args.data), Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    flow = pd.read_csv(data / "flow.csv", dtype={"stock_code": str}, parse_dates=["trade_date"]).rename(
        columns={"trade_date": "date"}
    )
    price = pd.read_csv(data / "price.csv", dtype={"stock_code": str}, parse_dates=["trade_date"]).rename(
        columns={"trade_date": "date"}
    )
    bench_all = pd.read_csv(data / "bench.csv", parse_dates=["trade_date"]).rename(columns={"trade_date": "date"})
    stocks = pd.read_csv(data / "stock.csv", dtype={"stock_code": str})
    bench = {m: bench_all[bench_all["index_code"] == m][["date", "close"]].sort_values("date") for m in MARKETS}

    market_of = dict(zip(stocks["stock_code"], stocks["market_type"], strict=True))
    price["market"] = price["stock_code"].map(market_of)
    price = price.dropna(subset=["market"])
    _selfcheck(price, bench, price["stock_code"].iloc[0], price["market"].iloc[0])

    flow_by_code = {c: g.drop(columns="stock_code") for c, g in flow.groupby("stock_code")}
    empty_flow = flow.iloc[0:0].drop(columns="stock_code")
    jobs = []
    counts = {m: 0 for m in MARKETS}
    for code, group in price.groupby("stock_code"):
        market = market_of[code]
        if args.limit_stocks and counts[market] >= args.limit_stocks:
            continue
        counts[market] += 1
        jobs.append((code, market, group.drop(columns=["stock_code", "market"]), flow_by_code.get(code, empty_flow)))
    print(f"[build] 종목 {len(jobs)}개 스코어 계산 중...", flush=True)
    with Pool(args.workers, initializer=_init_worker, initargs=(bench,)) as pool:
        frames = pool.map(_build_stock, jobs, chunksize=20)
    long = pd.concat(frames, ignore_index=True)
    long.to_pickle(out / "panel.pkl")

    rng = np.random.default_rng(SEED)
    report: dict = {"seed": SEED, "repeats": args.repeats, "markets": {}}
    for market in MARKETS:
        sub = long[long["market"] == market]
        pivots = {
            col: sub.pivot(index="date", columns="stock_code", values=col)
            for col in [*FLOW_CANDIDATES, *BASELINES, *(f"ex{h}" for h in ALL_HORIZONS), "liquid"]
        }
        dates = pivots["composite"].index.to_numpy()
        codes = pivots["composite"].columns.to_numpy()
        liquid = pivots["liquid"].fillna(False).to_numpy(dtype=bool)
        arrays = {k: v.to_numpy(dtype=float) for k, v in pivots.items() if k != "liquid"}
        flow_valid = np.isfinite(arrays[PRIMARY]) & liquid
        per_date = flow_valid.sum(axis=1)
        universe = per_date[per_date > 0]
        report["markets"][market] = {
            "universe_per_date": {
                "min": int(universe.min()),
                "median": int(np.median(universe)),
                "max": int(universe.max()),
            },
            "results": [],
        }
        print(f"[{market}] 유니버스(수급 유효 & 유동) 일평균 중앙값 {int(np.median(universe))}종목", flush=True)

        for horizon in ALL_HORIZONS:
            ret = arrays[f"ex{horizon}"]
            composite = arrays["composite"]
            for name in [*FLOW_CANDIDATES, *BASELINES]:
                score = arrays[name]
                base_mask = np.isfinite(score) & np.isfinite(ret) & liquid
                entries = [("raw", None, base_mask)]
                if name in FLOW_CANDIDATES:
                    entries.append(("vs_composite", composite, base_mask & np.isfinite(composite)))
                for mode, control, mask in entries:
                    stat = _evaluate(score, ret, control, mask, args.repeats, rng)
                    stat.update({"candidate": name, "horizon": horizon, "mode": mode})
                    if name == PRIMARY and mode == "raw":
                        stat["buckets"] = _buckets(score, ret, mask, dates, codes)
                    report["markets"][market]["results"].append(stat)
                    print(
                        f"  {market} h={horizon:>2} {name:<16} {mode:<12} ic={stat['mean_ic']} {stat.get('verdict', '')}",
                        flush=True,
                    )

    (out / "results.json").write_text(json.dumps(report, ensure_ascii=False, indent=1, default=float))
    _write_markdown(report, out / "report.md")
    print(f"[done] {out}/results.json, report.md")


def _fmt(stat: dict) -> str:
    if stat.get("mean_ic") is None:
        return "-"
    return f"{stat['mean_ic']:+.4f} {stat.get('verdict', '')}".strip()


def _write_markdown(report: dict, path: Path) -> None:
    lines = [f"# 수급 점수 null 테스트 (seed={report['seed']}, repeats={report['repeats']})", ""]
    for market, body in report["markets"].items():
        u = body["universe_per_date"]
        lines += [f"## {market} (유니버스 일별 {u['min']}~{u['max']}종목, 중앙값 {u['median']})", ""]
        lines += [
            "| 후보 | 모드 | " + " | ".join(f"h{h}" for h in ALL_HORIZONS) + " |",
            "|---|---|" + "---|" * len(ALL_HORIZONS),
        ]
        keys = sorted(
            {(r["candidate"], r["mode"]) for r in body["results"]}, key=lambda k: (k[0] not in FLOW_CANDIDATES, k)
        )
        for cand, mode in keys:
            cells = []
            for h in ALL_HORIZONS:
                match = [
                    r for r in body["results"] if r["candidate"] == cand and r["mode"] == mode and r["horizon"] == h
                ]
                cells.append(_fmt(match[0]) if match else "-")
            lines.append(f"| {cand} | {mode} | " + " | ".join(cells) + " |")
        lines.append("")
    lines += ["## 게이트 (smart_money, 판정 호라이즌 1/5/10/20, 두 시장 모두 ABOVE인 호라이즌 수)", ""]
    for mode in ("raw", "vs_composite"):
        passed = []
        for h in JUDGE_HORIZONS:
            ok = all(
                any(
                    r["candidate"] == PRIMARY
                    and r["mode"] == mode
                    and r["horizon"] == h
                    and r.get("verdict") == "ABOVE"
                    for r in report["markets"][m]["results"]
                )
                for m in MARKETS
            )
            if ok:
                passed.append(h)
        lines.append(f"- {mode}: {len(passed)}개 {passed} -> {'통과' if len(passed) >= 2 else '미달'} (기준 2개 이상)")
    path.write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
