import { useEffect, useRef } from 'react'
import { CandlestickSeries, ColorType, createChart } from 'lightweight-charts'
import type { DailyChartResponse } from '../../types/stock'

interface CandleChartProps {
  data: DailyChartResponse[]
}

export function CandleChart({ data }: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const chart = createChart(container, {
      layout: { textColor: '#374151', background: { type: ColorType.Solid, color: '#ffffff' } },
      grid: { vertLines: { color: '#f3f4f6' }, horzLines: { color: '#f3f4f6' } },
      width: container.clientWidth,
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
    })

    // 백엔드는 최신순(내림차순)으로 내려주므로 차트가 요구하는 오름차순으로 정렬한다.
    candleSeries.setData(
      data
        .slice()
        .sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
        .map((item) => ({
          time: item.tradeDate,
          open: item.open,
          high: item.high,
          low: item.low,
          close: item.close,
        })),
    )
    chart.timeScale().fitContent()

    const resizeObserver = new ResizeObserver((entries) => {
      chart.applyOptions({ width: entries[0].contentRect.width })
    })
    resizeObserver.observe(container)

    return () => {
      resizeObserver.disconnect()
      chart.remove()
    }
  }, [data])

  return <div ref={containerRef} className="w-full" />
}
