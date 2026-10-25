import { changeRateFromPrevious, formatChartPrice, formatChartVolume } from '../../utils/chartFormat'
import { changeRateColorClass, formatChangeRate } from '../../utils/priceFormat'

export interface OhlcInfo {
  label: string
  open: number
  high: number
  low: number
  close: number
  volume?: number
  prevClose?: number | null
}

interface ChartOhlcLegendProps {
  info: OhlcInfo | null
  precision: number
  /** 종가만 보여준다 - 시가=고가=저가=종가인 데이터(환율)에서 같은 값이 네 번 반복되는 걸 막는다. */
  closeOnly?: boolean
}

// 차트 좌상단에 겹쳐 보여주는 시가/고가/저가/종가 범례 - 크로스헤어를 올린 봉의 값, 올리지 않았을 땐 마지막 봉이다.
// lightweight-charts 기본 크로스헤어는 가격축 라벨만 찍어서 봉의 OHLC를 알 수 없었다.
// 색은 국내 관례(상승=빨강, 하락=파랑)를 따르고 전봉 종가 대비 등락률 기준이다.
export function ChartOhlcLegend({ info, precision, closeOnly = false }: ChartOhlcLegendProps) {
  if (!info) return null

  const changeRate = changeRateFromPrevious(info.close, info.prevClose)
  const valueClass = changeRateColorClass(changeRate ?? info.close - info.open)

  return (
    <div className="pointer-events-none absolute top-1 left-2 z-10 flex max-w-[calc(100%-1rem)] flex-wrap items-baseline gap-x-2 gap-y-0.5 rounded bg-white/80 px-1.5 py-0.5 text-[11px] sm:text-xs">
      <span className="text-gray-500">{info.label}</span>
      {!closeOnly && (
        <>
          <Item name="시" value={formatChartPrice(info.open, precision)} className={valueClass} />
          <Item name="고" value={formatChartPrice(info.high, precision)} className={valueClass} />
          <Item name="저" value={formatChartPrice(info.low, precision)} className={valueClass} />
        </>
      )}
      <Item name="종" value={formatChartPrice(info.close, precision)} className={valueClass} />
      {changeRate != null && <span className={valueClass}>{formatChangeRate(changeRate)}</span>}
      {info.volume != null && info.volume > 0 && (
        <span className="text-gray-500">
          거래량 <span className="text-gray-700">{formatChartVolume(info.volume)}</span>
        </span>
      )}
    </div>
  )
}

function Item({ name, value, className }: { name: string; value: string; className: string }) {
  return (
    <span className="text-gray-500">
      {name} <span className={className}>{value}</span>
    </span>
  )
}
