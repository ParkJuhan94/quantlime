import { useEffect, useMemo, useRef } from 'react'
import { CandlestickSeries, ColorType, HistogramSeries, createChart, type UTCTimestamp } from 'lightweight-charts'
import type { MinuteCandle } from '../../types/stock'

const UP_COLOR = '#dc2626'
const DOWN_COLOR = '#2563eb'
const KST = 'Asia/Seoul'

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: KST,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: KST,
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

interface MinuteCandleChartProps {
  candles: MinuteCandle[]
  currency: 'KRW' | 'USD'
}

// 분봉 전용 차트 - 일봉용 CandleChart는 'YYYY-MM-DD' 문자열 시간·지표·영업일 이동에 맞춰져 있어
// 그대로 쓰면 시간축 타입이 충돌하므로 별도 컴포넌트로 분리했다(캔들+거래량만, 지표 없음).
// lightweight-charts는 epoch 초를 UTC로 표시하므로 라벨은 KST로 직접 포맷한다.
export function MinuteCandleChart({ candles, currency }: MinuteCandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)

  // 페이지를 이어 붙이면 경계에서 같은 시각이 겹칠 수 있어 time 기준으로 중복을 제거한다.
  const sorted = useMemo(() => {
    const byTime = new Map<number, MinuteCandle>()
    candles.forEach((candle) => byTime.set(candle.time, candle))
    return [...byTime.values()].sort((a, b) => a.time - b.time)
  }, [candles])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const chart = createChart(container, {
      layout: { textColor: '#374151', background: { type: ColorType.Solid, color: '#ffffff' } },
      grid: { vertLines: { color: '#f3f4f6' }, horzLines: { color: '#f3f4f6' } },
      autoSize: true,
      height: 360,
      timeScale: {
        borderColor: '#e5e7eb',
        timeVisible: true,
        secondsVisible: false,
        tickMarkFormatter: (time: number) => timeFormatter.format(new Date(time * 1000)),
      },
      localization: {
        timeFormatter: (time: number) => dateTimeFormatter.format(new Date(time * 1000)),
      },
    })

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: UP_COLOR,
      downColor: DOWN_COLOR,
      borderVisible: false,
      wickUpColor: UP_COLOR,
      wickDownColor: DOWN_COLOR,
      priceFormat:
        currency === 'USD'
          ? { type: 'price', precision: 2, minMove: 0.01 }
          : { type: 'price', precision: 0, minMove: 1 },
    })
    candleSeries.setData(
      sorted.map((c) => ({ time: c.time as UTCTimestamp, open: c.open, high: c.high, low: c.low, close: c.close })),
    )

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    })
    chart.priceScale('volume').applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } })
    volumeSeries.setData(
      sorted.map((c) => ({
        time: c.time as UTCTimestamp,
        value: c.volume,
        color: c.close >= c.open ? UP_COLOR : DOWN_COLOR,
      })),
    )

    chart.timeScale().fitContent()
    return () => chart.remove()
  }, [sorted, currency])

  return <div ref={containerRef} className="w-full" />
}
