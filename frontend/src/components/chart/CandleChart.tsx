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

    return () => {
      chart.remove()
    }
  }, [data])

  return <div ref={containerRef} className="w-full" />
}
