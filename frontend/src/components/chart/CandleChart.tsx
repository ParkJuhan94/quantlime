import { useEffect, useRef } from 'react'
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  LineStyle,
  createChart,
  type ISeriesApi,
  type Time,
} from 'lightweight-charts'
import type { DailyChartResponse } from '../../types/stock'
import type { PriceBroadcastMessage } from '../../types/realtime'
import {
  calculateBollingerBands,
  calculateIchimoku,
  calculateMACD,
  calculateSMA,
  shiftBusinessDays,
  type IndicatorSettings,
} from '../../utils/indicators'

interface CandleChartProps {
  data: DailyChartResponse[]
  /** 기간 선택기가 요청한 실제 표시 구간(일). `data`는 이동평균 등 워밍업을
   * 위해 이보다 더 긴 이력을 담고 있을 수 있어, 화면엔 이 값만큼만 보여준다. */
  displayDays: number
  indicators: IndicatorSettings
  livePrice?: PriceBroadcastMessage
}

interface LiveBar {
  tradeDate: string
  open: number
  high: number
  low: number
  close: number
}

interface LinePoint {
  time: string
  value: number
}

function toLinePoints(values: Array<number | null>, dates: string[]): LinePoint[] {
  const points: LinePoint[] = []
  values.forEach((value, i) => {
    if (value != null) {
      points.push({ time: dates[i], value })
    }
  })
  return points
}

const MA_PERIODS = [5, 10, 20, 60, 120] as const
const MA_COLORS: Record<number, string> = {
  5: '#f59e0b',
  10: '#10b981',
  20: '#3b82f6',
  60: '#8b5cf6',
  120: '#ec4899',
}
const ICHIMOKU_DISPLACEMENT = 26
const UP_COLOR = 'rgba(220, 38, 38, 0.5)'
const DOWN_COLOR = 'rgba(37, 99, 235, 0.5)'

export function CandleChart({ data, displayDays, indicators, livePrice }: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  // 실시간 틱마다 오늘자 봉의 고가/저가/종가를 계산할 기준값. setData 이후
  // 틱으로 갱신되므로 매 렌더마다 새로 만들지 않고 ref로 들고 있는다.
  const lastBarRef = useRef<LiveBar | null>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    // 거래량/MACD는 하위 패널로 분리한다. 켜진 것만 패널을 배정해 빈
    // 패널이 생기지 않게 한다.
    let nextPaneIndex = 1
    const volumePaneIndex = indicators.volume ? nextPaneIndex++ : -1
    const macdPaneIndex = indicators.macd ? nextPaneIndex++ : -1
    const subPaneCount = (volumePaneIndex >= 0 ? 1 : 0) + (macdPaneIndex >= 0 ? 1 : 0)

    const chart = createChart(container, {
      layout: { textColor: '#374151', background: { type: ColorType.Solid, color: '#ffffff' } },
      grid: { vertLines: { color: '#f3f4f6' }, horzLines: { color: '#f3f4f6' } },
      // 직접 만든 ResizeObserver + clientWidth 조합은 좁은 화면에서 20px 정도
      // 우측이 컨테이너 밖으로 삐져나가는 문제가 있었다(Playwright로 375px
      // 뷰포트를 실제로 확인하며 발견) - 라이브러리 자체가 제공하는
      // autoSize로 바꿔 컨테이너 크기 측정/반영을 온전히 위임한다.
      autoSize: true,
      height: 360 + subPaneCount * 130,
      timeScale: { borderColor: '#e5e7eb' },
    })

    // 백엔드는 최신순(내림차순)으로 내려주므로 차트가 요구하는 오름차순으로 정렬한다.
    const sorted = data.slice().sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
    const dates = sorted.map((item) => item.tradeDate)
    const closes = sorted.map((item) => item.close)
    const highs = sorted.map((item) => item.high)
    const lows = sorted.map((item) => item.low)

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
    candleSeries.setData(
      sorted.map((item) => ({
        time: item.tradeDate,
        open: item.open,
        high: item.high,
        low: item.low,
        close: item.close,
      })),
    )
    candleSeriesRef.current = candleSeries
    const last = sorted[sorted.length - 1]
    lastBarRef.current = last ? { ...last } : null

    if (indicators.ma) {
      for (const period of MA_PERIODS) {
        const maSeries = chart.addSeries(LineSeries, {
          color: MA_COLORS[period],
          lineWidth: 1,
          priceLineVisible: false,
          lastValueVisible: false,
          title: `MA${period}`,
        })
        maSeries.setData(toLinePoints(calculateSMA(closes, period), dates))
      }
    }

    if (indicators.bollingerBands) {
      const { upper, middle, lower } = calculateBollingerBands(closes)
      const bands: Array<{ values: Array<number | null>; color: string; title: string }> = [
        { values: upper, color: '#94a3b8', title: 'BB상단' },
        { values: middle, color: '#64748b', title: 'BB중단' },
        { values: lower, color: '#94a3b8', title: 'BB하단' },
      ]
      for (const band of bands) {
        const bandSeries = chart.addSeries(LineSeries, {
          color: band.color,
          lineWidth: 1,
          lineStyle: LineStyle.Dashed,
          priceLineVisible: false,
          lastValueVisible: false,
          title: band.title,
        })
        bandSeries.setData(toLinePoints(band.values, dates))
      }
    }

    if (indicators.ichimoku) {
      const { tenkan, kijun, senkouA, senkouB, chikou } = calculateIchimoku(highs, lows, closes)

      const tenkanSeries = chart.addSeries(LineSeries, {
        color: '#ef4444',
        lineWidth: 1,
        priceLineVisible: false,
        lastValueVisible: false,
        title: '전환선',
      })
      tenkanSeries.setData(toLinePoints(tenkan, dates))

      const kijunSeries = chart.addSeries(LineSeries, {
        color: '#0ea5e9',
        lineWidth: 1,
        priceLineVisible: false,
        lastValueVisible: false,
        title: '기준선',
      })
      kijunSeries.setData(toLinePoints(kijun, dates))

      // 선행스팬A/B는 오늘로부터 26일 뒤(구름대)에, 후행스팬은 26일
      // 전(과거 가격 위)에 겹쳐 그리는 게 일목균형표의 정의다.
      const senkouASeries = chart.addSeries(LineSeries, {
        color: '#22c55e',
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        priceLineVisible: false,
        lastValueVisible: false,
        title: '선행스팬A',
      })
      senkouASeries.setData(
        toLinePoints(senkouA, dates).map((point) => ({
          ...point,
          time: shiftBusinessDays(point.time, ICHIMOKU_DISPLACEMENT),
        })),
      )

      const senkouBSeries = chart.addSeries(LineSeries, {
        color: '#f97316',
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        priceLineVisible: false,
        lastValueVisible: false,
        title: '선행스팬B',
      })
      senkouBSeries.setData(
        toLinePoints(senkouB, dates).map((point) => ({
          ...point,
          time: shiftBusinessDays(point.time, ICHIMOKU_DISPLACEMENT),
        })),
      )

      const chikouSeries = chart.addSeries(LineSeries, {
        color: '#a855f7',
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        priceLineVisible: false,
        lastValueVisible: false,
        title: '후행스팬',
      })
      chikouSeries.setData(
        toLinePoints(chikou, dates).map((point) => ({
          ...point,
          time: shiftBusinessDays(point.time, -ICHIMOKU_DISPLACEMENT),
        })),
      )
    }

    if (volumePaneIndex >= 0) {
      const volumeSeries = chart.addSeries(
        HistogramSeries,
        { priceFormat: { type: 'volume' }, priceLineVisible: false, lastValueVisible: false },
        volumePaneIndex,
      )
      volumeSeries.setData(
        sorted.map((item) => ({
          time: item.tradeDate,
          value: item.volume,
          color: item.close >= item.open ? UP_COLOR : DOWN_COLOR,
        })),
      )
    }

    if (macdPaneIndex >= 0) {
      const { macdLine, signalLine, histogram } = calculateMACD(closes)

      const histogramSeries = chart.addSeries(
        HistogramSeries,
        { priceLineVisible: false, lastValueVisible: false },
        macdPaneIndex,
      )
      const histogramPoints: Array<{ time: string; value: number; color: string }> = []
      histogram.forEach((value, i) => {
        if (value != null) {
          histogramPoints.push({ time: dates[i], value, color: value >= 0 ? UP_COLOR : DOWN_COLOR })
        }
      })
      histogramSeries.setData(histogramPoints)

      const macdSeries = chart.addSeries(
        LineSeries,
        { color: '#2563eb', lineWidth: 1, priceLineVisible: false, lastValueVisible: false, title: 'MACD' },
        macdPaneIndex,
      )
      macdSeries.setData(toLinePoints(macdLine, dates))

      const signalSeries = chart.addSeries(
        LineSeries,
        { color: '#f97316', lineWidth: 1, priceLineVisible: false, lastValueVisible: false, title: 'Signal' },
        macdPaneIndex,
      )
      signalSeries.setData(toLinePoints(signalLine, dates))
    }

    const panes = chart.panes()
    panes[0]?.setStretchFactor(subPaneCount > 0 ? 5 : 1)
    if (volumePaneIndex >= 0) panes[volumePaneIndex]?.setStretchFactor(1.5)
    if (macdPaneIndex >= 0) panes[macdPaneIndex]?.setStretchFactor(1.5)

    // MA120/일목 선행스팬 워밍업을 위해 `data`가 표시 구간(displayDays)보다
    // 더 긴 이력을 담고 있을 수 있으므로, fitContent 대신 실제 표시할
    // 구간만 visible range로 지정한다. 일목균형표가 켜져 있으면 구름대가
    // 26영업일 미래까지 그려지므로 그만큼 우측 여백도 넓힌다.
    const displayFromIndex = Math.max(0, sorted.length - displayDays)
    const visibleFrom = dates[displayFromIndex]
    const lastDate = dates[dates.length - 1]
    const visibleTo = indicators.ichimoku && lastDate ? shiftBusinessDays(lastDate, ICHIMOKU_DISPLACEMENT) : lastDate
    if (visibleFrom && visibleTo) {
      chart.timeScale().setVisibleRange({ from: visibleFrom as Time, to: visibleTo as Time })
    } else {
      chart.timeScale().fitContent()
    }

    return () => {
      candleSeriesRef.current = null
      chart.remove()
    }
  }, [data, displayDays, indicators])

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
