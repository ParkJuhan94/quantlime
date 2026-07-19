import type {
  IChartApi,
  IPrimitivePaneRenderer,
  IPrimitivePaneView,
  ISeriesApi,
  ISeriesPrimitive,
  PrimitivePaneViewZOrder,
  SeriesAttachedParameter,
  Time,
} from 'lightweight-charts'
import type { CanvasRenderingTarget2D } from 'fancy-canvas'

export interface BandFillPoint {
  time: string
  value: number
}

class BandFillRenderer implements IPrimitivePaneRenderer {
  private readonly upper: BandFillPoint[]
  private readonly lower: BandFillPoint[]
  private readonly color: string
  private readonly chart: IChartApi
  private readonly series: ISeriesApi<'Line'>

  constructor(upper: BandFillPoint[], lower: BandFillPoint[], color: string, chart: IChartApi, series: ISeriesApi<'Line'>) {
    this.upper = upper
    this.lower = lower
    this.color = color
    this.chart = chart
    this.series = series
  }

  draw(target: CanvasRenderingTarget2D): void {
    target.useMediaCoordinateSpace(({ context }) => {
      const timeScale = this.chart.timeScale()
      const upperPoints: [number, number][] = []
      const lowerPoints: [number, number][] = []

      for (const point of this.upper) {
        const x = timeScale.timeToCoordinate(point.time as Time)
        const y = this.series.priceToCoordinate(point.value)
        if (x != null && y != null) upperPoints.push([x, y])
      }
      for (const point of this.lower) {
        const x = timeScale.timeToCoordinate(point.time as Time)
        const y = this.series.priceToCoordinate(point.value)
        if (x != null && y != null) lowerPoints.push([x, y])
      }
      if (upperPoints.length < 2 || lowerPoints.length < 2) return

      context.save()
      context.beginPath()
      context.moveTo(upperPoints[0][0], upperPoints[0][1])
      for (const [x, y] of upperPoints) context.lineTo(x, y)
      for (let i = lowerPoints.length - 1; i >= 0; i -= 1) context.lineTo(lowerPoints[i][0], lowerPoints[i][1])
      context.closePath()
      context.fillStyle = this.color
      context.fill()
      context.restore()
    })
  }
}

class BandFillPaneView implements IPrimitivePaneView {
  private readonly primitive: BandFillPrimitive

  constructor(primitive: BandFillPrimitive) {
    this.primitive = primitive
  }

  zOrder(): PrimitivePaneViewZOrder {
    // 밴드 라인(upper/lower)이 채우기 위에 또렷하게 남도록 뒤쪽에 그린다.
    return 'bottom'
  }

  renderer(): IPrimitivePaneRenderer | null {
    const { upper, lower, color, chart, series } = this.primitive
    if (!chart || !series) return null
    return new BandFillRenderer(upper, lower, color, chart, series)
  }
}

/**
 * 볼린저밴드 상/하단, 일목균형표 구름대(선행스팬A/B)처럼 두 라인 사이를
 * 옅게 채우는 용도의 커스텀 시리즈 프리미티브. lightweight-charts는
 * "두 라인 사이 채우기"를 기본 시리즈 타입으로 제공하지 않는다(AreaSeries는
 * 라인에서 baseline까지만 채운다) - 공식 플러그인 예제(bands-indicator)와
 * 동일한 방식으로 캔버스에 직접 폴리곤을 그린다. upper 라인 시리즈에
 * attachPrimitive로 붙여서 쓴다.
 */
export class BandFillPrimitive implements ISeriesPrimitive<Time> {
  upper: BandFillPoint[]
  lower: BandFillPoint[]
  color: string
  chart: IChartApi | null = null
  series: ISeriesApi<'Line'> | null = null

  private readonly paneViewInstance = new BandFillPaneView(this)

  constructor(upper: BandFillPoint[], lower: BandFillPoint[], color: string) {
    this.upper = upper
    this.lower = lower
    this.color = color
  }

  attached(param: SeriesAttachedParameter<Time>): void {
    this.chart = param.chart
    this.series = param.series as ISeriesApi<'Line'>
  }

  detached(): void {
    this.chart = null
    this.series = null
  }

  updateAllViews(): void {}

  paneViews(): readonly IPrimitivePaneView[] {
    return [this.paneViewInstance]
  }
}
