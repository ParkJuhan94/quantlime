import { Link } from 'react-router-dom'
import type { WatchlistResponse } from '../../types/watchlist'
import type { PriceBroadcastMessage } from '../../types/realtime'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../../utils/priceFormat'

interface WatchlistRowProps {
  item: WatchlistResponse
  livePrice?: PriceBroadcastMessage
  onRemove: (stockCode: string) => void
}

export function WatchlistRow({ item, livePrice, onRemove }: WatchlistRowProps) {
  return (
    <tr className="border-b border-gray-100">
      <td className="py-2">
        <Link to={`/stocks/${item.stockCode}`} className="font-medium text-gray-900 hover:underline">
          {item.stockName}
        </Link>
        <span className="ml-2 text-xs text-gray-400">{item.stockCode}</span>
      </td>
      <td className="py-2 text-sm text-gray-600">{item.marketType}</td>
      <td className="py-2 text-sm text-gray-600">{item.sector}</td>
      <td className="py-2 text-right text-sm text-gray-900">{formatPrice(livePrice?.currentPrice)}</td>
      <td className={`py-2 text-right text-sm ${changeRateColorClass(livePrice?.changeRate)}`}>
        {formatChangeRate(livePrice?.changeRate)}
      </td>
      <td className="py-2 text-right">
        <button
          type="button"
          onClick={() => onRemove(item.stockCode)}
          className="text-xs text-red-600 hover:underline"
        >
          삭제
        </button>
      </td>
    </tr>
  )
}
