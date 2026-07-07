import { apiClient } from './client'
import type {
  CurrentPriceResponse,
  DailyChartResponse,
  PageResponse,
  StockDetailResponse,
} from '../types/stock'
import type { ScoreResponse } from '../types/score'

export async function searchStocks(
  q: string,
  page = 0,
  size = 20,
): Promise<PageResponse<StockDetailResponse>> {
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

/** 스코어가 아직 계산되지 않은 경우 백엔드가 404(SC_000)를 반환한다 -
 * 호출 측에서 이를 에러가 아니라 정상적인 빈 상태로 다뤄야 한다. */
export async function getScore(stockCode: string): Promise<ScoreResponse> {
  const { data } = await apiClient.get<ScoreResponse>(`/api/stocks/${stockCode}/score`)
  return data
}
