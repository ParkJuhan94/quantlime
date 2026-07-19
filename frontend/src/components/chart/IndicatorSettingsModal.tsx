import { useEffect, useState } from 'react'
import { DEFAULT_INDICATOR_SETTINGS, type IndicatorSettings } from '../../utils/indicators'
import { IndicatorDetailView, type DetailIndicatorKey } from './IndicatorDetailView'

interface IndicatorSettingsModalProps {
  open: boolean
  value: IndicatorSettings
  onClose: () => void
  onSave: (next: IndicatorSettings) => void
}

type IndicatorToggleKey = 'volume' | 'ma' | 'bollingerBands' | 'ichimoku' | 'macd'
type Tab = 'top' | 'bottom'

interface IndicatorRowConfig {
  key: IndicatorToggleKey
  tab: Tab
  name: string
  description: string
  detailKey?: DetailIndicatorKey
}

// "상단 지표"는 캔들 위에 겹쳐 그리는 오버레이, "하단 지표"는 별도
// 패널(거래량/MACD)로 분리해서 그린다 - CandleChart의 실제 렌더링
// 방식과 그대로 대응시킨다. 참고 이미지엔 "설정" 탭도 있었지만, 지금
// 앱엔 그 탭에 넣을 만한 실제 전역 설정(예: 캔들 색상 테마 등)이 없어
// 눌러도 아무것도 안 뜨는 빈 탭을 만들지 않기로 했다 - 실제로 동작하는
// 두 탭만 남김.
const ROWS: IndicatorRowConfig[] = [
  {
    key: 'ma',
    tab: 'top',
    name: '이동평균선',
    description: '일정 기간의 평균 가격을 선으로 이어 추세를 보여줘요',
    detailKey: 'ma',
  },
  {
    key: 'bollingerBands',
    tab: 'top',
    name: '볼린저밴드',
    description: '가격 변동폭을 상단·하단 밴드로 표시해 과매수·과매도를 가늠해요',
    detailKey: 'bollingerBands',
  },
  {
    key: 'ichimoku',
    tab: 'top',
    name: '일목균형표',
    description: '여러 이동평균선을 조합해 추세와 지지·저항을 함께 보여줘요',
    detailKey: 'ichimoku',
  },
  {
    key: 'volume',
    tab: 'bottom',
    name: '거래량',
    description: '일별로 거래된 주식 수를 막대로 표시해요',
  },
  {
    key: 'macd',
    tab: 'bottom',
    name: 'MACD',
    description: '단기·장기 이동평균의 차이로 추세 전환 시점을 가늠해요',
    detailKey: 'macd',
  },
]

const TABS: { key: Tab; label: string }[] = [
  { key: 'top', label: '상단 지표' },
  { key: 'bottom', label: '하단 지표' },
]

export function ToggleSwitch({ checked, onChange, label }: { checked: boolean; onChange: (next: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-10 shrink-0 rounded-full transition-colors ${checked ? 'bg-gray-900' : 'bg-gray-200'}`}
    >
      {/* left을 명시하지 않으면 absolute 요소의 static position이 트랙
          오른쪽 끝으로 잡혀(브라우저가 auto 값을 그렇게 해석) translate가
          거기서 더 오른쪽으로 밀려나 트랙 밖으로 삐져나갔다 - left-0.5를
          기준점으로 명시하고 translate-x는 거기서부터의 이동량만 준다. */}
      <span
        className={`absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-4' : 'translate-x-0'
        }`}
      />
    </button>
  )
}

// 참고 이미지(토스 HTS 지표 설정 화면)를 기준으로 재설계 - 탭(상단/하단
// 지표) 안에 지표 목록(이름 + 한 줄 설명 + "상세 설정하기" + 토글)을
// 보여주고, "상세 설정하기"를 누르면 그 지표 전용 화면(IndicatorDetailView)
// 으로 드릴다운한다. 값 변경은 즉시 draft에 반영하되, 실제 차트에는
// "저장하기"를 눌러야 커밋된다(예전 버전과 동일한 draft 분리 원칙).
export function IndicatorSettingsModal({ open, value, onClose, onSave }: IndicatorSettingsModalProps) {
  const [draft, setDraft] = useState<IndicatorSettings>(value)
  const [activeTab, setActiveTab] = useState<Tab>('top')
  const [activeDetail, setActiveDetail] = useState<DetailIndicatorKey | null>(null)

  useEffect(() => {
    if (open) {
      setDraft(value)
      setActiveTab('top')
      setActiveDetail(null)
    }
  }, [open, value])

  useEffect(() => {
    if (!open) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  function handleSave() {
    onSave(draft)
    onClose()
  }

  const visibleRows = ROWS.filter((row) => row.tab === activeTab)

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="flex max-h-[85vh] w-full max-w-md flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
          <p className="text-base font-semibold text-gray-900">지표 설정</p>
          <div className="flex items-center gap-3">
            {activeDetail === null && (
              <button
                type="button"
                onClick={() => {
                  if (window.confirm('모든 지표 설정을 기본값으로 되돌릴까요?')) {
                    setDraft(DEFAULT_INDICATOR_SETTINGS)
                  }
                }}
                className="text-xs font-medium text-gray-400 hover:text-gray-600"
              >
                초기화 하기
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              aria-label="닫기"
              className="flex h-7 w-7 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100"
            >
              ×
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {activeDetail !== null ? (
            <IndicatorDetailView
              indicatorKey={activeDetail}
              draft={draft}
              onChange={setDraft}
              onBack={() => setActiveDetail(null)}
            />
          ) : (
            <>
              <div className="mb-4 flex gap-1 rounded-xl bg-gray-100 p-1">
                {TABS.map((tab) => (
                  <button
                    key={tab.key}
                    type="button"
                    onClick={() => setActiveTab(tab.key)}
                    className={`flex-1 rounded-lg py-1.5 text-sm font-semibold transition ${
                      activeTab === tab.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>

              <div className="flex flex-col divide-y divide-gray-50">
                {visibleRows.map((row) => (
                  <div key={row.key} className="flex items-center justify-between gap-3 py-3.5">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-gray-900">{row.name}</p>
                      <p className="mt-0.5 truncate text-xs text-gray-400">{row.description}</p>
                      {row.detailKey && (
                        <button
                          type="button"
                          onClick={() => setActiveDetail(row.detailKey!)}
                          className="mt-1 text-xs font-medium text-gray-700 hover:underline"
                        >
                          상세 설정하기 ›
                        </button>
                      )}
                    </div>
                    <ToggleSwitch
                      checked={draft[row.key]}
                      onChange={(checked) => setDraft((prev) => ({ ...prev, [row.key]: checked }))}
                      label={`${row.name} 표시`}
                    />
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {activeDetail === null && (
          <div className="border-t border-gray-100 p-4">
            <button
              type="button"
              onClick={handleSave}
              className="w-full rounded-lg bg-gray-900 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
            >
              저장하기
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
