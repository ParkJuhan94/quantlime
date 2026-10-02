import { apiClient } from './client'
import type {
  CurrentPriceResponse,
  DailyChartResponse,
  OrderbookResponse,
  MinuteChartResponse,
  PageResponse,
  PriceLimitResponse,
  StockDetailResponse,
  StockFundamentalsResponse,
  StockWarningResponse,
  TradeResponse,
} from '../types/stock'
import type { ScoreResponse } from '../types/score'

export async function searchStocks(q: string, page = 0, size = 20): Promise<PageResponse<StockDetailResponse>> {
  const { data } = await apiClient.get<PageResponse<StockDetailResponse>>('/api/stocks/search', {
    params: { q, page, size },
  })
  return data
}

export async function getStock(stockCode: string): Promise<StockDetailResponse> {
  const { data } = await apiClient.get<StockDetailResponse>(`/api/stocks/${stockCode}`)
  return data
}

export async function getCurrentPrice(stockCode: string): Promise<CurrentPriceResponse> {
  const { data } = await apiClient.get<CurrentPriceResponse>(`/api/stocks/${stockCode}/price`)
  return data
}

export async function getChart(stockCode: string, days = 90): Promise<DailyChartResponse[]> {
  const { data } = await apiClient.get<DailyChartResponse[]>(`/api/stocks/${stockCode}/chart`, {
    params: { period: 'daily', days },
  })
  return data
}

export async function getFundamentals(stockCode: string): Promise<StockFundamentalsResponse> {
  const { data } = await apiClient.get<StockFundamentalsResponse>(`/api/stocks/${stockCode}/fundamentals`)
  return data
}

export async function getPopularStocks(limit = 5): Promise<StockDetailResponse[]> {
  const { data } = await apiClient.get<StockDetailResponse[]>('/api/stocks/popular', { params: { limit } })
  return data
}

/** 스코어가 아직 계산되지 않은 경우 백엔드가 404(SC_000)를 반환한다 -
 * 호출 측에서 이를 에러가 아니라 정상적인 빈 상태로 다뤄야 한다. */
export async function getScore(stockCode: string): Promise<ScoreResponse> {
  const { data } = await apiClient.get<ScoreResponse>(`/api/stocks/${stockCode}/score`)
  return data
}

export async function getOrderbook(stockCode: string): Promise<OrderbookResponse> {
  const { data } = await apiClient.get<OrderbookResponse>(`/api/stocks/${stockCode}/orderbook`)
  return data
}

export async function getTrades(stockCode: string): Promise<TradeResponse[]> {
  const { data } = await apiClient.get<TradeResponse[]>(`/api/stocks/${stockCode}/trades`)
  return data
}

export async function getPriceLimit(stockCode: string): Promise<PriceLimitResponse> {
  const { data } = await apiClient.get<PriceLimitResponse>(`/api/stocks/${stockCode}/price-limits`)
  return data
}

export async function getStockWarnings(stockCode: string): Promise<StockWarningResponse[]> {
  const { data } = await apiClient.get<StockWarningResponse[]>(`/api/stocks/${stockCode}/warnings`)
  return data
}

// before(UTC Z 표기)를 생략하면 가장 최근 1분봉부터 최대 200개 - 서버가 Redis로 15초 캐싱한다.
export async function getMinuteChart(stockCode: string, before?: string): Promise<MinuteChartResponse> {
  const { data } = await apiClient.get<MinuteChartResponse>(`/api/stocks/${stockCode}/minute-chart`, {
    params: before ? { before } : undefined,
  })
  return data
}
