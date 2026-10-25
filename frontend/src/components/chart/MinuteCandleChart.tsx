import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  TickMarkType,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts'
import type { MinuteCandle } from '../../types/stock'
import { chartPalette } from './chartTheme'
import { subscribeReachStart } from './chartInteraction'
import { ChartOhlcLegend, type OhlcInfo } from './ChartOhlcLegend'
import { useTheme } from '../../hooks/useTheme'
import {
  formatKstDate,
  formatKstDateTime,
  formatKstMonthDay,
  formatKstTime,
  priceFormatOptions,
} from '../../utils/chartFormat'

const UP_COLOR = '#dc2626'
const DOWN_COLOR = '#2563eb'

interface MinuteCandleChartProps {
  /** 시간 오름차순·중복 없음(normalizeMinuteCandles) + 원하는 단위로 이미 묶인 봉. */
  candles: MinuteCandle[]
  /** 가격 소수 자리 - 국내 주식·비트코인은 0, 해외 주식·지수는 2. */
  pricePrecision: number
  /** 'time'은 HH:mm 축(분봉), 'date'는 날짜 축(epoch 초로 내려오는 일/주/월봉). */
  timeMode?: 'time' | 'date'
  /** 이 값이 바뀔 때(종목·단위 전환)만 전체 봉이 보이도록 시야를 맞춘다. 같은 키에서 데이터만 바뀌면(1분 갱신,
   * 과거 페이지 이어받기) 사용자가 보던 위치를 그대로 둔다. */
  viewKey: string
  /** 왼쪽 끝까지 스크롤했을 때 더 과거 구간을 불러오는 콜백. */
  onReachStart?: () => void
}

function tickLabel(time: Time, type: TickMarkType, timeMode: 'time' | 'date'): string {
  const seconds = Number(time)
  if (timeMode === 'time' && (type === TickMarkType.Time || type === TickMarkType.TimeWithSeconds)) {
    return formatKstTime(seconds)
  }
  return formatKstMonthDay(seconds)
}

// 분봉 전용 차트 - 일봉용 CandleChart는 'YYYY-MM-DD' 문자열 시간·지표·영업일 이동에 맞춰져 있어
// 그대로 쓰면 시간축 타입이 충돌하므로 별도 컴포넌트로 분리했다(캔들+거래량만, 지표 없음).
// lightweight-charts는 epoch 초를 UTC로 표시하므로 라벨은 KST로 직접 포맷한다.
//
// 차트는 테마/자릿수/축 모드가 바뀔 때만 다시 만들고, 데이터는 setData로 갈아 끼운다 - 1분 갱신이나 과거 구간
// 이어받기마다 차트를 새로 만들면 줌/스크롤 위치가 매번 초기화된다.
export function MinuteCandleChart({
  candles,
  pricePrecision,
  timeMode = 'time',
  viewKey,
  onReachStart,
}: MinuteCandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<{ candle: ISeriesApi<'Candlestick'>; volume: ISeriesApi<'Histogram'> } | null>(null)
  const candlesRef = useRef(candles)
  const viewKeyRef = useRef(viewKey)
  const fittedViewKeyRef = useRef<string | null>(null)
  const onReachStartRef = useRef(onReachStart)
  const [hoverTime, setHoverTime] = useState<number | null>(null)
  const { theme } = useTheme()

  useEffect(() => {
    candlesRef.current = candles
    viewKeyRef.current = viewKey
    onReachStartRef.current = onReachStart
  })

  const applyData = useCallback(() => {
    const chart = chartRef.current
    const series = seriesRef.current
    const data = candlesRef.current
    if (!chart || !series) return

    series.candle.setData(
      data.map((c) => ({ time: c.time as UTCTimestamp, open: c.open, high: c.high, low: c.low, close: c.close })),
    )
    series.volume.setData(
      data.map((c) => ({
        time: c.time as UTCTimestamp,
        value: c.volume,
        color: c.close >= c.open ? UP_COLOR : DOWN_COLOR,
      })),
    )
    if (data.length > 0 && fittedViewKeyRef.current !== viewKeyRef.current) {
      chart.timeScale().fitContent()
      fittedViewKeyRef.current = viewKeyRef.current
    }
  }, [])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const palette = chartPalette(theme)
    const chart = createChart(container, {
      layout: { textColor: palette.text, background: { type: ColorType.Solid, color: palette.background } },
      grid: { vertLines: { color: palette.grid }, horzLines: { color: palette.grid } },
      autoSize: true,
      height: 360,
      timeScale: {
        borderColor: palette.border,
        timeVisible: timeMode === 'time',
        secondsVisible: false,
        tickMarkFormatter: (time: Time, type: TickMarkType) => tickLabel(time, type, timeMode),
      },
      localization: {
        timeFormatter: (time: number) => (timeMode === 'time' ? formatKstDateTime(time) : formatKstDate(time)),
      },
    })

    const candle = chart.addSeries(CandlestickSeries, {
      upColor: UP_COLOR,
      downColor: DOWN_COLOR,
      borderVisible: false,
      wickUpColor: UP_COLOR,
      wickDownColor: DOWN_COLOR,
      priceFormat: priceFormatOptions(pricePrecision),
    })
    const volume = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    })
    chart.priceScale('volume').applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } })

    chart.subscribeCrosshairMove((param) => setHoverTime(param.time == null ? null : Number(param.time)))
    subscribeReachStart(chart, onReachStartRef)

    chartRef.current = chart
    seriesRef.current = { candle, volume }
    fittedViewKeyRef.current = null
    applyData()

    return () => {
      chartRef.current = null
      seriesRef.current = null
      chart.remove()
    }
  }, [theme, pricePrecision, timeMode, applyData])

  useEffect(() => {
    applyData()
  }, [candles, viewKey, applyData])

  const indexByTime = useMemo(() => new Map(candles.map((c, i) => [c.time, i])), [candles])
  const info = useMemo<OhlcInfo | null>(() => {
    if (candles.length === 0) return null
    const index = hoverTime == null ? candles.length - 1 : (indexByTime.get(hoverTime) ?? candles.length - 1)
    const candle = candles[index]
    return {
      label: timeMode === 'time' ? formatKstDateTime(candle.time) : formatKstDate(candle.time),
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close,
      volume: candle.volume,
      prevClose: index > 0 ? candles[index - 1].close : null,
    }
  }, [candles, hoverTime, indexByTime, timeMode])

  return (
    <div className="relative">
      <div ref={containerRef} className="w-full" style={{ height: 360 }} />
      <ChartOhlcLegend info={info} precision={pricePrecision} />
    </div>
  )
}
