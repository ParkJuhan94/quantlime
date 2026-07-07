import type { StockDetailResponse } from '../../types/stock'

interface SearchResultItemProps {
  stock: StockDetailResponse
  disabled?: boolean
  onAdd: (stockCode: string) => void
}

export function SearchResultItem({ stock, disabled, onAdd }: SearchResultItemProps) {
  return (
    <li className="flex items-center justify-between px-3 py-2 hover:bg-gray-50">
      <div>
        <p className="font-medium text-gray-900">{stock.stockName}</p>
        <p className="text-xs text-gray-500">
          {stock.stockCode} · {stock.marketType} · {stock.sector}
        </p>
      </div>
      <button
        type="button"
        disabled={disabled}
        onClick={() => onAdd(stock.stockCode)}
        className="rounded-md bg-gray-900 px-3 py-1 text-xs font-medium text-white transition hover:bg-gray-800 disabled:opacity-50"
      >
        추가
      </button>
    </li>
  )
}
