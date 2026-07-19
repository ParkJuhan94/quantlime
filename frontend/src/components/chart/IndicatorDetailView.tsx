import { ColorWidthChip } from './ColorWidthChip'
import { ToggleSwitch } from './IndicatorSettingsModal'
import { PRICE_SOURCE_OPTIONS } from './indicatorPalette'
import {
  DEFAULT_INDICATOR_SETTINGS,
  type IndicatorSettings,
  type LineStyleSettings,
  type MaLineSettings,
  type PriceSource,
} from '../../utils/indicators'

export type DetailIndicatorKey = 'ma' | 'bollingerBands' | 'ichimoku' | 'macd'

const DETAIL_TITLES: Record<DetailIndicatorKey, string> = {
  ma: '이동평균선',
  bollingerBands: '볼린저밴드',
  ichimoku: '일목균형표',
  macd: 'MACD',
}

// 참고 이미지의 "지난 n일 동안의 주가 평균값을 이은 선" 같은 한 줄
// 설명 - 목록 화면(IndicatorSettingsModal)의 설명과 같은 문구를 재사용해
// 두 화면이 다른 말을 하지 않게 한다.
const DETAIL_DESCRIPTIONS: Record<DetailIndicatorKey, string> = {
  ma: '지난 n일 동안의 주가 평균값을 이은 선',
  bollingerBands: '가격 변동폭을 상단·하단 밴드로 표시해 과매수·과매도를 가늠해요',
  ichimoku: '여러 이동평균선을 조합해 추세와 지지·저항을 함께 보여줘요',
  macd: '단기·장기 이동평균의 차이로 추세 전환 시점을 가늠해요',
}

// 지표별로 "초기화"를 누르면 그 지표에 해당하는 필드만 기본값으로
// 되돌린다(전체 초기화는 목록 화면에 이미 있음 - 여기선 지금 보고 있는
// 지표만).
function resetIndicator(indicatorKey: DetailIndicatorKey, draft: IndicatorSettings): IndicatorSettings {
  switch (indicatorKey) {
    case 'ma':
      return { ...draft, maLines: DEFAULT_INDICATOR_SETTINGS.maLines }
    case 'bollingerBands':
      return {
        ...draft,
        bollingerBandsParams: DEFAULT_INDICATOR_SETTINGS.bollingerBandsParams,
        bollingerBandsLines: DEFAULT_INDICATOR_SETTINGS.bollingerBandsLines,
      }
    case 'ichimoku':
      return {
        ...draft,
        ichimokuParams: DEFAULT_INDICATOR_SETTINGS.ichimokuParams,
        ichimokuLines: DEFAULT_INDICATOR_SETTINGS.ichimokuLines,
      }
    case 'macd':
      return {
        ...draft,
        macdParams: DEFAULT_INDICATOR_SETTINGS.macdParams,
        macdLines: DEFAULT_INDICATOR_SETTINGS.macdLines,
      }
  }
}

const MAX_MA_LINES = 8
const MA_COLOR_ROTATION = ['#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', '#ec4899', '#ef4444', '#14b8a6', '#f43f5e']

interface IndicatorDetailViewProps {
  indicatorKey: DetailIndicatorKey
  draft: IndicatorSettings
  onChange: (next: IndicatorSettings) => void
  onBack: () => void
}

// 라벨 텍스트가 "기간"인 단독 필드는 화면에 굳이 표시하지 않는다(값
// 옆에 붙는 다른 필드와 겹쳐서 안 봐도 문맥상 기간임을 알 수 있고, 짧은
// 라벨을 계속 나열하면 화면이 번잡해 보인다는 피드백) - 접근성용
// aria-label은 유지한다. "전환선 기간"처럼 무엇의 기간인지 구분이
// 필요한 라벨은 그대로 보여준다.
function PeriodNumberInput({
  label,
  value,
  onChange,
}: {
  label: string
  value: number
  onChange: (next: number) => void
}) {
  if (label === '기간') {
    return (
      <input
        aria-label={label}
        type="number"
        min={1}
        value={value}
        onChange={(event) => {
          const next = Number(event.target.value)
          if (Number.isFinite(next) && next >= 1) onChange(next)
        }}
        className="self-end rounded-lg border border-gray-200 px-2 py-1.5 text-center text-sm text-gray-900 outline-none focus:border-gray-400"
      />
    )
  }

  return (
    <label className="flex flex-col gap-1 text-xs text-gray-500">
      {label}
      <input
        type="number"
        min={1}
        value={value}
        onChange={(event) => {
          const next = Number(event.target.value)
          if (Number.isFinite(next) && next >= 1) onChange(next)
        }}
        className="w-full rounded-lg border border-gray-200 px-2 py-1.5 text-center text-sm text-gray-900 outline-none focus:border-gray-400"
      />
    </label>
  )
}

function LineDetailRow({
  label,
  line,
  onChange,
}: {
  label: string
  line: LineStyleSettings
  onChange: (next: LineStyleSettings) => void
}) {
  return (
    <div className="flex items-center justify-between gap-2 rounded-xl border border-gray-100 px-3 py-2.5">
      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={line.visible}
          onChange={(event) => onChange({ ...line, visible: event.target.checked })}
          className="h-3.5 w-3.5 rounded border-gray-300"
        />
        {label}
      </label>
      <ColorWidthChip line={line} onChange={onChange} />
    </div>
  )
}

function MaDetail({ maLines, onChange }: { maLines: MaLineSettings[]; onChange: (next: MaLineSettings[]) => void }) {
  function update(index: number, patch: Partial<MaLineSettings>) {
    onChange(maLines.map((line, i) => (i === index ? { ...line, ...patch } : line)))
  }

  function remove(index: number) {
    onChange(maLines.filter((_, i) => i !== index))
  }

  function add() {
    const color = MA_COLOR_ROTATION[maLines.length % MA_COLOR_ROTATION.length]
    onChange([...maLines, { visible: true, color, width: 1, lineStyle: 'solid', period: 20, priceSource: 'close' }])
  }

  return (
    <div className="flex flex-col gap-3">
      {maLines.map((line, index) => (
        <div key={index} className="rounded-xl border border-gray-100 p-3">
          <div className="mb-2.5 flex items-center justify-between">
            <label className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <input
                type="checkbox"
                checked={line.visible}
                onChange={(event) => update(index, { visible: event.target.checked })}
                className="h-3.5 w-3.5 rounded border-gray-300"
              />
              기간 {index + 1}
            </label>
            {maLines.length > 1 && (
              <button
                type="button"
                aria-label={`기간 ${index + 1} 삭제`}
                onClick={() => remove(index)}
                className="flex h-6 w-6 items-center justify-center rounded-full text-gray-300 hover:bg-gray-100 hover:text-gray-600"
              >
                ×
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <ColorWidthChip line={line} onChange={(next) => update(index, next)} />
            <select
              aria-label="가격 기준"
              value={line.priceSource}
              onChange={(event) => update(index, { priceSource: event.target.value as PriceSource })}
              className="flex-1 rounded-lg border border-gray-200 px-2 py-1.5 text-sm text-gray-900 outline-none focus:border-gray-400"
            >
              {PRICE_SOURCE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <input
              aria-label="기간"
              type="number"
              min={1}
              value={line.period}
              onChange={(event) => {
                const next = Number(event.target.value)
                if (Number.isFinite(next) && next >= 1) update(index, { period: next })
              }}
              className="w-16 shrink-0 rounded-lg border border-gray-200 px-2 py-1.5 text-center text-sm text-gray-900 outline-none focus:border-gray-400"
            />
          </div>
        </div>
      ))}
      {maLines.length < MAX_MA_LINES && (
        <button
          type="button"
          onClick={add}
          className="w-full rounded-xl border border-dashed border-gray-300 py-2.5 text-sm font-semibold text-gray-500 hover:bg-gray-50"
        >
          + 기간 추가
        </button>
      )}
    </div>
  )
}

// 이동평균은 기간을 사용자가 자유롭게 추가/삭제할 수 있어(참고 이미지
// 그대로) 별도 컴포넌트(MaDetail)로 분리했다. 볼린저밴드/일목균형표/MACD는
// 라인 구성 자체가 고정이라(예: 일목균형표는 항상 전환선/기준선/선행스팬A·B/
// 후행스팬 5개) 파라미터 입력 + 고정 라인 목록으로 충분하다.
export function IndicatorDetailView({ indicatorKey, draft, onChange, onBack }: IndicatorDetailViewProps) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          aria-label="뒤로"
          className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100"
        >
          ‹
        </button>
        <button
          type="button"
          onClick={() => {
            if (window.confirm(`${DETAIL_TITLES[indicatorKey]} 설정을 기본값으로 되돌릴까요?`)) {
              onChange(resetIndicator(indicatorKey, draft))
            }
          }}
          className="text-xs font-medium text-gray-400 hover:text-gray-600"
        >
          초기화 하기
        </button>
      </div>
      <p className="mb-1 text-base font-semibold text-gray-900">{DETAIL_TITLES[indicatorKey]}</p>
      <p className="mb-4 text-xs text-gray-400">{DETAIL_DESCRIPTIONS[indicatorKey]}</p>

      {indicatorKey === 'ma' && (
        <MaDetail maLines={draft.maLines} onChange={(maLines) => onChange({ ...draft, maLines })} />
      )}

      {indicatorKey === 'bollingerBands' && (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-2 gap-2">
            <PeriodNumberInput
              label="기간"
              value={draft.bollingerBandsParams.period}
              onChange={(period) =>
                onChange({ ...draft, bollingerBandsParams: { ...draft.bollingerBandsParams, period } })
              }
            />
            <PeriodNumberInput
              label="표준편차 배수"
              value={draft.bollingerBandsParams.multiplier}
              onChange={(multiplier) =>
                onChange({ ...draft, bollingerBandsParams: { ...draft.bollingerBandsParams, multiplier } })
              }
            />
          </div>
          <div className="flex items-center justify-between rounded-xl border border-gray-100 px-3 py-2.5">
            <span className="text-sm text-gray-700">상단-하단 밴드 사이 색채움</span>
            <ToggleSwitch
              checked={draft.bollingerBandsFill}
              onChange={(bollingerBandsFill) => onChange({ ...draft, bollingerBandsFill })}
              label="상단-하단 밴드 사이 색채움"
            />
          </div>
          <LineDetailRow
            label="BB상단"
            line={draft.bollingerBandsLines.upper}
            onChange={(upper) => onChange({ ...draft, bollingerBandsLines: { ...draft.bollingerBandsLines, upper } })}
          />
          <LineDetailRow
            label="BB중단"
            line={draft.bollingerBandsLines.middle}
            onChange={(middle) =>
              onChange({ ...draft, bollingerBandsLines: { ...draft.bollingerBandsLines, middle } })
            }
          />
          <LineDetailRow
            label="BB하단"
            line={draft.bollingerBandsLines.lower}
            onChange={(lower) => onChange({ ...draft, bollingerBandsLines: { ...draft.bollingerBandsLines, lower } })}
          />
        </div>
      )}

      {indicatorKey === 'ichimoku' && (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-3 gap-2">
            <PeriodNumberInput
              label="전환선 기간"
              value={draft.ichimokuParams.tenkanPeriod}
              onChange={(tenkanPeriod) =>
                onChange({ ...draft, ichimokuParams: { ...draft.ichimokuParams, tenkanPeriod } })
              }
            />
            <PeriodNumberInput
              label="기준선 기간"
              value={draft.ichimokuParams.kijunPeriod}
              onChange={(kijunPeriod) =>
                onChange({ ...draft, ichimokuParams: { ...draft.ichimokuParams, kijunPeriod } })
              }
            />
            <PeriodNumberInput
              label="선행스팬B 기간"
              value={draft.ichimokuParams.senkouBPeriod}
              onChange={(senkouBPeriod) =>
                onChange({ ...draft, ichimokuParams: { ...draft.ichimokuParams, senkouBPeriod } })
              }
            />
          </div>
          <div className="flex items-center justify-between rounded-xl border border-gray-100 px-3 py-2.5">
            <span className="text-sm text-gray-700">구름대(선행스팬A-B 사이) 색채움</span>
            <ToggleSwitch
              checked={draft.ichimokuCloudFill}
              onChange={(ichimokuCloudFill) => onChange({ ...draft, ichimokuCloudFill })}
              label="구름대 색채움"
            />
          </div>
          <LineDetailRow
            label="전환선"
            line={draft.ichimokuLines.tenkan}
            onChange={(tenkan) => onChange({ ...draft, ichimokuLines: { ...draft.ichimokuLines, tenkan } })}
          />
          <LineDetailRow
            label="기준선"
            line={draft.ichimokuLines.kijun}
            onChange={(kijun) => onChange({ ...draft, ichimokuLines: { ...draft.ichimokuLines, kijun } })}
          />
          <LineDetailRow
            label="선행스팬A"
            line={draft.ichimokuLines.senkouA}
            onChange={(senkouA) => onChange({ ...draft, ichimokuLines: { ...draft.ichimokuLines, senkouA } })}
          />
          <LineDetailRow
            label="선행스팬B"
            line={draft.ichimokuLines.senkouB}
            onChange={(senkouB) => onChange({ ...draft, ichimokuLines: { ...draft.ichimokuLines, senkouB } })}
          />
          <LineDetailRow
            label="후행스팬"
            line={draft.ichimokuLines.chikou}
            onChange={(chikou) => onChange({ ...draft, ichimokuLines: { ...draft.ichimokuLines, chikou } })}
          />
        </div>
      )}

      {indicatorKey === 'macd' && (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-3 gap-2">
            <PeriodNumberInput
              label="단기"
              value={draft.macdParams.fastPeriod}
              onChange={(fastPeriod) => onChange({ ...draft, macdParams: { ...draft.macdParams, fastPeriod } })}
            />
            <PeriodNumberInput
              label="장기"
              value={draft.macdParams.slowPeriod}
              onChange={(slowPeriod) => onChange({ ...draft, macdParams: { ...draft.macdParams, slowPeriod } })}
            />
            <PeriodNumberInput
              label="시그널"
              value={draft.macdParams.signalPeriod}
              onChange={(signalPeriod) =>
                onChange({ ...draft, macdParams: { ...draft.macdParams, signalPeriod } })
              }
            />
          </div>
          <LineDetailRow
            label="MACD"
            line={draft.macdLines.macd}
            onChange={(macd) => onChange({ ...draft, macdLines: { ...draft.macdLines, macd } })}
          />
          <LineDetailRow
            label="Signal"
            line={draft.macdLines.signal}
            onChange={(signal) => onChange({ ...draft, macdLines: { ...draft.macdLines, signal } })}
          />
        </div>
      )}

      <button
        type="button"
        onClick={onBack}
        className="mt-5 w-full rounded-lg bg-gray-900 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
      >
        저장
      </button>
    </div>
  )
}
