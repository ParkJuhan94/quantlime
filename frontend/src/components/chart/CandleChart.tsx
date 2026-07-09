import { useEffect, useRef } from 'react'
import { CandlestickSeries, ColorType, createChart, type ISeriesApi } from 'lightweight-charts'
import type { DailyChartResponse } from '../../types/stock'
import type { PriceBroadcastMessage } from '../../types/realtime'

interface CandleChartProps {
  data: DailyChartResponse[]
  livePrice?: PriceBroadcastMessage
}

interface LiveBar {
  tradeDate: string
  open: number
  high: number
  low: number
  close: number
}

export function CandleChart({ data, livePrice }: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  // 실시간 틱마다 오늘자 봉의 고가/저가/종가를 계산할 기준값. setData 이후
  // 틱으로 갱신되므로 매 렌더마다 새로 만들지 않고 ref로 들고 있는다.
  const lastBarRef = useRef<LiveBar | null>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const chart = createChart(container, {
      layout: { textColor: '#374151', background: { type: ColorType.Solid, color: '#ffffff' } },
      grid: { vertLines: { color: '#f3f4f6' }, horzLines: { color: '#f3f4f6' } },
      // 직접 만든 ResizeObserver + clientWidth 조합은 좁은 화면에서 20px 정도
      // 우측이 컨테이너 밖으로 삐져나가는 문제가 있었다(Playwright로 375px
      // 뷰포트를 실제로 확인하며 발견) - 라이브러리 자체가 제공하는
      // autoSize로 바꿔 컨테이너 크기 측정/반영을 온전히 위임한다.
      autoSize: true,
      height: 360,
      timeScale: { borderColor: '#e5e7eb' },
    })

    // 국내 주식 관례: 상승은 빨강, 하락은 파랑(미국식과 반대).
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#dc2626',
      downColor: '#2563eb',
      borderVisible: false,
      wickUpColor: '#dc2626',
      wickDownColor: '#2563eb',
      // 국내 주식은 원 단위 정수 가격이라 라이브러리 기본값(소수 2자리)을
      // 끄고 정수로 표시한다(크로스헤어/축 라벨 전부 적용됨).
      priceFormat: { type: 'price', precision: 0, minMove: 1 },
    })

    // 백엔드는 최신순(내림차순)으로 내려주므로 차트가 요구하는 오름차순으로 정렬한다.
    const sorted = data.slice().sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
    candleSeries.setData(
      sorted.map((item) => ({
        time: item.tradeDate,
        open: item.open,
        high: item.high,
        low: item.low,
        close: item.close,
      })),
    )
    chart.timeScale().fitContent()

    candleSeriesRef.current = candleSeries
    const last = sorted[sorted.length - 1]
    lastBarRef.current = last ? { ...last } : null

    return () => {
      candleSeriesRef.current = null
      chart.remove()
    }
  }, [data])

  // 일별 배치가 아직 안 돈 시간대(장중)에도 오늘자 캔들이 실시간 가격을
  // 따라 움직이도록, 틱마다 마지막 봉(또는 아직 없으면 오늘자 신규 봉)을
  // 갱신한다. 오늘자 봉이 아직 DB에 없는 경우 첫 틱 가격을 시가로 삼는
  // 근사치다(정확한 시가는 다음 배치/수동 트리거가 채워줄 때 대체된다).
  useEffect(() => {
    const series = candleSeriesRef.current
    if (!series || livePrice?.currentPrice == null) return

    const today = livePrice.timestamp.slice(0, 10)
    const price = livePrice.currentPrice
    const previous = lastBarRef.current

    const bar: LiveBar =
      previous && previous.tradeDate === today
        ? { ...previous, high: Math.max(previous.high, price), low: Math.min(previous.low, price), close: price }
        : { tradeDate: today, open: price, high: price, low: price, close: price }

    series.update({ time: bar.tradeDate, open: bar.open, high: bar.high, low: bar.low, close: bar.close })
    lastBarRef.current = bar
  }, [livePrice])

  return <div ref={containerRef} className="w-full" />
}
