// 백엔드 StockMapper와 동일한 네이버 금융 비공식 정적 로고 경로 규칙(공식 API
// 아님). WatchlistResponse처럼 DTO에 logoUrl이 없는 응답에서, 이미 알고 있는
// stockCode만으로 로고 경로를 그대로 재구성할 때 쓴다.
export function buildStockLogoUrl(stockCode: string): string {
  return `https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock${stockCode}.png`
}
