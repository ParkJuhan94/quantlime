import pytest

from calculator.cross_sectional import MIN_STOCKS_PER_DATE
from calculator.normalization import (
    MEAN_REVERSION_WEIGHT,
    TREND_WEIGHT,
    StockAxisScores,
    normalize_cross_section,
)


def _scores(n: int, overrides: dict[int, dict] | None = None) -> list[StockAxisScores]:
    """n개 종목을 균일 분포(0~n-1)로 채운 기본 리스트. overrides는
    {index: {"trend_score": ..., "mean_reversion_score": ...}} 형태로 특정
    인덱스만 덮어쓴다."""
    base = [
        StockAxisScores(
            stock_code=f"{i:06d}",
            trend_score=float(i),
            mean_reversion_score=float(i),
        )
        for i in range(n)
    ]
    for idx, fields in (overrides or {}).items():
        for key, value in fields.items():
            setattr(base[idx], key, value)
    return base


class TestMinimumSampleGate:
    def test_below_minimum_sample_yields_no_percentiles(self):
        # given: MIN_STOCKS_PER_DATE 미만(예: 5종목)
        scores = _scores(MIN_STOCKS_PER_DATE - 1)

        # when
        result = normalize_cross_section(scores)

        # then: 그 날짜의 횡단면 순위 자체가 불안정하므로 아무것도 채우지 않는다
        assert result.min_sample_met is False
        assert len(result.items) == len(scores)
        assert all(item.composite_percentile is None for item in result.items)

    def test_at_minimum_sample_computes_percentiles(self):
        # given: 정확히 MIN_STOCKS_PER_DATE
        scores = _scores(MIN_STOCKS_PER_DATE)

        # when
        result = normalize_cross_section(scores)

        # then
        assert result.min_sample_met is True
        assert result.items[-1].trend_percentile == pytest.approx(100.0)


class TestPercentileRanking:
    def test_highest_raw_score_gets_percentile_100(self):
        # given: 20종목, trend_score가 0~19로 서로 다른 값
        scores = _scores(20)

        # when
        result = normalize_cross_section(scores)

        # then: 가장 높은 원점수(19) -> 백분위 100, 가장 낮은 원점수(0) -> 100/20=5
        by_code = {item.stock_code: item for item in result.items}
        assert by_code["000019"].trend_percentile == pytest.approx(100.0)
        assert by_code["000000"].trend_percentile == pytest.approx(5.0)

    def test_tied_scores_share_average_rank(self):
        # given: 20종목 중 마지막 두 종목이 동점(19)
        scores = _scores(20)
        scores[18].trend_score = 19.0
        scores[18].mean_reversion_score = 19.0

        # when
        result = normalize_cross_section(scores)

        # then: 19/20위 동점 -> 평균 순위(19.5/20*100=97.5)를 공유
        by_code = {item.stock_code: item for item in result.items}
        assert by_code["000018"].trend_percentile == pytest.approx(97.5)
        assert by_code["000019"].trend_percentile == pytest.approx(97.5)

    def test_missing_axis_score_yields_none_percentile_without_diluting_others(self):
        # given: 20종목 중 1종목만 trend_score 없음(None)
        scores = _scores(20, {0: {"trend_score": None}})

        # when
        result = normalize_cross_section(scores)

        # then: 결측 종목은 None, 나머지 19종목은 유효 표본(19)만으로 순위 계산
        by_code = {item.stock_code: item for item in result.items}
        assert by_code["000000"].trend_percentile is None
        # 두 번째로 낮은 원점수(1)가 이제 19종목 중 최하위 -> 100/19
        assert by_code["000001"].trend_percentile == pytest.approx(100.0 / 19)


class TestCompositePercentile:
    def test_composite_requires_both_axes(self):
        # given: 20종목 중 1종목은 추세추종 축만 없음(평균회귀는 있음)
        scores = _scores(20, {5: {"trend_score": None}})

        # when
        result = normalize_cross_section(scores)

        # then: v3.0 scorer.calculate_score의 "단일축 종합점수 금지"와 동일 원칙
        by_code = {item.stock_code: item for item in result.items}
        assert by_code["000005"].mean_reversion_percentile is not None
        assert by_code["000005"].composite_percentile is None

    def test_composite_is_percentile_of_weighted_axis_percentiles(self):
        # given: 추세추종은 오름차순(0~19), 평균회귀는 내림차순(19~0)이라
        # 종목마다 두 축 백분위 합이 항상 100 근처로 대칭
        n = 20
        scores = [
            StockAxisScores(stock_code=f"{i:06d}", trend_score=float(i), mean_reversion_score=float(n - 1 - i))
            for i in range(n)
        ]

        # when
        result = normalize_cross_section(scores)

        # then: 가중치가 균등(0.5/0.5)이므로 모든 종목의 (trend_pctl + mr_pctl)/2가
        # 거의 동일 -> 종합 백분위끼리도 사실상 동률(모두 같은 순위 근방)
        assert TREND_WEIGHT == pytest.approx(0.5)
        assert MEAN_REVERSION_WEIGHT == pytest.approx(0.5)
        composites = [item.composite_percentile for item in result.items]
        # 완전히 대칭이라 가중합이 전부 같은 값(52.5) -> 전원 동점 ->
        # average rank = (n+1)/2 = 10.5 -> 백분위 10.5/20*100 = 52.5
        assert composites == pytest.approx([52.5] * n)
