import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  LineStyle,
  createChart,
  type ISeriesApi,
  type LineWidth,
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
  type LineDashStyle,
  type LineStyleSettings,
} from '../../utils/indicators'
import { BandFillPrimitive, type BandFillPoint } from './BandFillPrimitive'

// lightweight-charts의 LineWidth는 1~4 정수만 허용한다 - 설정값은
// 자유 입력(number)으로 저장하므로 차트에 넘기기 직전에 클램프한다.
function toLineWidth(width: number): LineWidth {
  return Math.min(4, Math.max(1, Math.round(width))) as LineWidth
}

// 저장된 옛 설정(선 종류 필드 추가 이전)엔 lineStyle이 없을 수 있어
// 실선으로 안전하게 폴백한다.
function toLineStyle(style: LineDashStyle | undefined): LineStyle {
  if (style === 'dashed') return LineStyle.Dashed
  if (style === 'dotted') return LineStyle.Dotted
  return LineStyle.Solid
}

// hex 색상을 지정한 투명도의 rgba로 변환한다 - 밴드/구름대 채우기는
// 라인 색상 그대로 쓰면 너무 진해서 옅게 눌러야 한다.
function withAlpha(hexColor: string, alpha: number): string {
  const hex = hexColor.replace('#', '')
  const r = parseInt(hex.slice(0, 2), 16)
  const g = parseInt(hex.slice(2, 4), 16)
  const b = parseInt(hex.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

interface CandleChartProps {
  data: DailyChartResponse[]
  /** 기간 선택기가 요청한 실제 표시 구간(일). `data`는 이동평균 등 워밍업을
   * 위해 이보다 더 긴 이력을 담고 있을 수 있어, 화면엔 이 값만큼만 보여준다. */
  displayDays: number
  indicators: IndicatorSettings
  livePrice?: PriceBroadcastMessage
}

// 지표 라벨(IndicatorLegend)을 클릭했을 때 "이게 이 라인이에요"라고
// 알려주는 하이라이트 애니메이션을 부모(StockDetailPage)가 트리거할 수
// 있게 노출하는 명령형 핸들. 라벨-라인 매칭 키는 IndicatorLegend가
// 만드는 key와 정확히 같은 규칙(ma-{index}/bb-upper/ichimoku-tenkan/
// macd-macd 등)을 따라야 한다.
export interface CandleChartHandle {
  highlightSeries: (key: string) => void
}

interface RegisteredLine {
  series: ISeriesApi<'Line'>
  width: LineWidth
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

const UP_COLOR = 'rgba(220, 38, 38, 0.5)'
const DOWN_COLOR = 'rgba(37, 99, 235, 0.5)'

export const CandleChart = forwardRef<CandleChartHandle, CandleChartProps>(function CandleChart(
  { data, displayDays, indicators, livePrice },
  ref,
) {
  const containerRef = useRef<HTMLDivElement>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  // 실시간 틱마다 오늘자 봉의 고가/저가/종가를 계산할 기준값. setData 이후
  // 틱으로 갱신되므로 매 렌더마다 새로 만들지 않고 ref로 들고 있는다.
  const lastBarRef = useRef<LiveBar | null>(null)
  // 지표 라벨 클릭 → 하이라이트 대상 조회용. 매 useEffect 실행마다
  // (지표 설정이 바뀌어 시리즈가 재생성될 때마다) 통째로 다시 채운다.
  const lineRegistryRef = useRef<Record<string, RegisteredLine>>({})

  useImperativeHandle(ref, () => ({
    highlightSeries(key: string) {
      const entry = lineRegistryRef.current[key]
      if (!entry) return
      const { series, width } = entry
      const highlightWidth = toLineWidth(width + 2)
      // 두 번 깜빡이며 굵어졌다 원래대로 돌아오는 펄스 - "이게 이
      // 라인이에요"를 짧게 강조하고 원래 두께로 조용히 복귀한다.
      const steps: [LineWidth, number][] = [
        [highlightWidth, 0],
        [width, 220],
        [highlightWidth, 440],
        [width, 680],
      ]
      steps.forEach(([lineWidth, delay]) => {
        setTimeout(() => {
          if (lineRegistryRef.current[key]?.series === series) {
            series.applyOptions({ lineWidth })
          }
        }, delay)
      })
    },
  }))

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    lineRegistryRef.current = {}

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
    const opens = sorted.map((item) => item.open)
    const highs = sorted.map((item) => item.high)
    const lows = sorted.map((item) => item.low)
    const sourceByType: Record<string, number[]> = { close: closes, open: opens, high: highs, low: lows }

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

    // 구름대 시프트(선행/후행)는 관례상 기준선(kijun) 기간과 같게 맞춘다.
    // 일목균형표가 꺼져 있어도 표시 구간(visibleRange) 계산에 필요해
    // 조건 블록 밖에서 미리 구해둔다.
    const ichimokuDisplacement = indicators.ichimokuParams.kijunPeriod

    if (indicators.ma) {
      indicators.maLines.forEach((line, index) => {
        // 개별 라인 단위로도 끌 수 있다(예: MA120만 숨기기) - title은
        // 항상 비워 y축에 지표 라벨이 찍히지 않게 한다. 이름/색상은
        // IndicatorLegend가 같은 설정을 참조해 차트 바깥의 접이식
        // 범례로 대신 보여준다.
        if (!line.visible) return
        const maSeries = chart.addSeries(LineSeries, {
          color: line.color,
          lineWidth: toLineWidth(line.width),
          lineStyle: toLineStyle(line.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        const source = sourceByType[line.priceSource ?? 'close'] ?? closes
        maSeries.setData(toLinePoints(calculateSMA(source, line.period), dates))
        lineRegistryRef.current[`ma-${index}`] = { series: maSeries, width: toLineWidth(line.width) }
      })
    }

    if (indicators.bollingerBands) {
      const { upper, middle, lower } = calculateBollingerBands(
        closes, indicators.bollingerBandsParams.period, indicators.bollingerBandsParams.multiplier)
      const bands: Array<{ key: string; values: Array<number | null>; line: LineStyleSettings }> = [
        { key: 'bb-upper', values: upper, line: indicators.bollingerBandsLines.upper },
        { key: 'bb-middle', values: middle, line: indicators.bollingerBandsLines.middle },
        { key: 'bb-lower', values: lower, line: indicators.bollingerBandsLines.lower },
      ]
      let upperSeries: ISeriesApi<'Line'> | null = null
      let lowerSeries: ISeriesApi<'Line'> | null = null
      for (const band of bands) {
        if (!band.line.visible) continue
        const bandSeries = chart.addSeries(LineSeries, {
          color: band.line.color,
          lineWidth: toLineWidth(band.line.width),
          lineStyle: toLineStyle(band.line.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        lineRegistryRef.current[band.key] = { series: bandSeries, width: toLineWidth(band.line.width) }
        bandSeries.setData(toLinePoints(band.values, dates))
        if (band === bands[0]) upperSeries = bandSeries
        if (band === bands[2]) lowerSeries = bandSeries
      }
      // 상단-하단 밴드가 둘 다 표시 중일 때만 그 사이를 채운다.
      if (indicators.bollingerBandsFill && upperSeries && lowerSeries) {
        const upperPoints: BandFillPoint[] = toLinePoints(upper, dates)
        const lowerPoints: BandFillPoint[] = toLinePoints(lower, dates)
        upperSeries.attachPrimitive(
          new BandFillPrimitive(upperPoints, lowerPoints, withAlpha(indicators.bollingerBandsLines.upper.color, 0.12)),
        )
      }
    }

    if (indicators.ichimoku) {
      const { tenkanPeriod, kijunPeriod, senkouBPeriod } = indicators.ichimokuParams
      const { tenkan, kijun, senkouA, senkouB, chikou } = calculateIchimoku(
        highs, lows, closes, tenkanPeriod, kijunPeriod, senkouBPeriod)
      const { tenkan: tenkanLine, kijun: kijunLine, senkouA: senkouALine,
        senkouB: senkouBLine, chikou: chikouLine } = indicators.ichimokuLines

      if (tenkanLine.visible) {
        const tenkanSeries = chart.addSeries(LineSeries, {
          color: tenkanLine.color,
          lineWidth: toLineWidth(tenkanLine.width),
          lineStyle: toLineStyle(tenkanLine.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        tenkanSeries.setData(toLinePoints(tenkan, dates))
        lineRegistryRef.current['ichimoku-tenkan'] = { series: tenkanSeries, width: toLineWidth(tenkanLine.width) }
      }

      if (kijunLine.visible) {
        const kijunSeries = chart.addSeries(LineSeries, {
          color: kijunLine.color,
          lineWidth: toLineWidth(kijunLine.width),
          lineStyle: toLineStyle(kijunLine.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        kijunSeries.setData(toLinePoints(kijun, dates))
        lineRegistryRef.current['ichimoku-kijun'] = { series: kijunSeries, width: toLineWidth(kijunLine.width) }
      }

      // 선행스팬A/B는 오늘로부터 기준선 기간만큼 뒤(구름대)에, 후행스팬은
      // 그만큼 전(과거 가격 위)에 겹쳐 그리는 게 일목균형표의 정의다.
      let senkouASeriesRef: ISeriesApi<'Line'> | null = null
      let senkouAPoints: BandFillPoint[] = []
      let senkouBPoints: BandFillPoint[] = []
      if (senkouALine.visible) {
        const senkouASeries = chart.addSeries(LineSeries, {
          color: senkouALine.color,
          lineWidth: toLineWidth(senkouALine.width),
          lineStyle: toLineStyle(senkouALine.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        senkouAPoints = toLinePoints(senkouA, dates).map((point) => ({
          ...point,
          time: shiftBusinessDays(point.time, ichimokuDisplacement),
        }))
        senkouASeries.setData(senkouAPoints)
        senkouASeriesRef = senkouASeries
        lineRegistryRef.current['ichimoku-senkouA'] = { series: senkouASeries, width: toLineWidth(senkouALine.width) }
      }

      if (senkouBLine.visible) {
        const senkouBSeries = chart.addSeries(LineSeries, {
          color: senkouBLine.color,
          lineWidth: toLineWidth(senkouBLine.width),
          lineStyle: toLineStyle(senkouBLine.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        senkouBPoints = toLinePoints(senkouB, dates).map((point) => ({
          ...point,
          time: shiftBusinessDays(point.time, ichimokuDisplacement),
        }))
        senkouBSeries.setData(senkouBPoints)
        lineRegistryRef.current['ichimoku-senkouB'] = { series: senkouBSeries, width: toLineWidth(senkouBLine.width) }
      }

      // 구름대(선행스팬A-B 사이) 채우기 - 둘 다 표시 중일 때만 의미가 있다.
      if (indicators.ichimokuCloudFill && senkouASeriesRef && senkouAPoints.length > 0 && senkouBPoints.length > 0) {
        senkouASeriesRef.attachPrimitive(
          new BandFillPrimitive(senkouAPoints, senkouBPoints, withAlpha(senkouALine.color, 0.15)),
        )
      }

      if (chikouLine.visible) {
        const chikouSeries = chart.addSeries(LineSeries, {
          color: chikouLine.color,
          lineWidth: toLineWidth(chikouLine.width),
          lineStyle: toLineStyle(chikouLine.lineStyle),
          priceLineVisible: false,
          lastValueVisible: false,
          title: '',
        })
        chikouSeries.setData(
          toLinePoints(chikou, dates).map((point) => ({
            ...point,
            time: shiftBusinessDays(point.time, -ichimokuDisplacement),
          })),
        )
        lineRegistryRef.current['ichimoku-chikou'] = { series: chikouSeries, width: toLineWidth(chikouLine.width) }
      }
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
      const { fastPeriod, slowPeriod, signalPeriod } = indicators.macdParams
      const { macdLine, signalLine, histogram } = calculateMACD(closes, fastPeriod, slowPeriod, signalPeriod)

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

      if (indicators.macdLines.macd.visible) {
        const macdSeries = chart.addSeries(
          LineSeries,
          {
            color: indicators.macdLines.macd.color,
            lineWidth: toLineWidth(indicators.macdLines.macd.width),
            lineStyle: toLineStyle(indicators.macdLines.macd.lineStyle),
            priceLineVisible: false,
            lastValueVisible: false,
            title: '',
          },
          macdPaneIndex,
        )
        macdSeries.setData(toLinePoints(macdLine, dates))
        lineRegistryRef.current['macd-macd'] = { series: macdSeries, width: toLineWidth(indicators.macdLines.macd.width) }
      }

      if (indicators.macdLines.signal.visible) {
        const signalSeries = chart.addSeries(
          LineSeries,
          {
            color: indicators.macdLines.signal.color,
            lineWidth: toLineWidth(indicators.macdLines.signal.width),
            lineStyle: toLineStyle(indicators.macdLines.signal.lineStyle),
            priceLineVisible: false,
            lastValueVisible: false,
            title: '',
          },
          macdPaneIndex,
        )
        signalSeries.setData(toLinePoints(signalLine, dates))
        lineRegistryRef.current['macd-signal'] = { series: signalSeries, width: toLineWidth(indicators.macdLines.signal.width) }
      }
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
    const visibleTo = indicators.ichimoku && lastDate ? shiftBusinessDays(lastDate, ichimokuDisplacement) : lastDate
    if (visibleFrom && visibleTo) {
      chart.timeScale().setVisibleRange({ from: visibleFrom as Time, to: visibleTo as Time })
    } else {
      chart.timeScale().fitContent()
    }

    return () => {
      candleSeriesRef.current = null
      lineRegistryRef.current = {}
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
})
