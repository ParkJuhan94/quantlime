import { apiClient } from './client'
import type { BacktestResponse } from '../types/backtest'

export async function getBacktest(stockCode: string): Promise<BacktestResponse> {
  const { data } = await apiClient.get<BacktestResponse>(`/api/backtest/${stockCode}`)
  return data
}
