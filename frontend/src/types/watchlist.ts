export interface WatchlistResponse {
  id: number
  stockCode: string
  stockName: string
  marketType: string
  sector: string
  groupId: number | null
  sortOrder: number
  createdAt: string
}

export interface WatchlistGroupResponse {
  id: number
  name: string
  sortOrder: number
}
