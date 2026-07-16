"""QuantLab 퀀트 엔진 (FastAPI).

Spring 백엔드가 관심 종목의 OHLCV 이력을 넘기면, 기술적 지표를 계산해
추세추종/평균회귀 서브스코어 + 종합점수 + 등급 + AI 코멘트를 반환한다.
"""

from __future__ import annotations

import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from calculator.commentary import generate_comment
from calculator.indicators import compute_all_indicators
from calculator.scorer import calculate_score
from schemas import (
    DivergenceResponse,
    ScoreBatchRequest,
    ScoreBatchResponse,
    StockScoreRequest,
    StockScoreResponse,
)

load_dotenv()

app = FastAPI(title="QuantLab Quant Engine", version="0.1.0")

# Prometheus가 스크랩할 /metrics를 노출한다(요청 수/지연을 자동 계측).
# Spring 쪽 Micrometer와 마찬가지로 멀티서비스 관측성 스택(Phase 1)의
# 일부 - quant-engine은 요청량이 적어(배치 호출 위주) 커스텀 지표보다는
# 기본 HTTP 계측만으로 충분하다고 판단해 최소 설정만 적용한다.
Instrumentator().instrument(app).expose(app)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def _score_single_stock(stock: StockScoreRequest) -> StockScoreResponse:
    df = pd.DataFrame([item.model_dump() for item in stock.ohlcv])
    df = df.sort_values("date").reset_index(drop=True)

    enriched = compute_all_indicators(df)
    latest = enriched.iloc[-1].to_dict()

    score = calculate_score(latest)
    comment = generate_comment(stock.stock_code, score, latest)

    divergence_response = None
    if score.divergence is not None:
        divergence_response = DivergenceResponse(
            flag=score.divergence.flag, message=score.divergence.message
        )

    return StockScoreResponse(
        stock_code=stock.stock_code,
        trend_score=score.trend_score,
        mean_reversion_score=score.mean_reversion_score,
        composite_score=score.composite_score,
        grade=score.grade,
        divergence=divergence_response,
        comment=comment,
        insufficient_data=score.insufficient_data,
    )


@app.post("/calculate/score/batch", response_model=ScoreBatchResponse)
def calculate_score_batch(request: ScoreBatchRequest) -> ScoreBatchResponse:
    results = [_score_single_stock(stock) for stock in request.stocks]
    return ScoreBatchResponse(scores=results)
