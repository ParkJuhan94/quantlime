interface FilterChipOption<T> {
  key: T
  label: string
}

interface FilterChipGroupProps<T> {
  options: FilterChipOption<T>[]
  selected: T
  onSelect: (key: T) => void
}

// 홈 실시간 랭킹(RankingTable)의 전체/국내/해외·정렬 필터에서 쓰던 칩 스타일
// (rounded-xl 래퍼 + rounded-lg 버튼, 선택은 배경색으로 표시)을 VideoFeedPage
// 채널 필터가 세 번째로 재구현하고 있어 공용 컴포넌트로 추출했다(Phase 8
// P7-F1) - 텔레그램 요약 피드 채널 필터도 이걸 그대로 쓴다. T는 문자열/숫자/
// undefined 등 React key로 안전하게 직렬화 가능한 타입만 쓸 것.
export function FilterChipGroup<T>({ options, selected, onSelect }: FilterChipGroupProps<T>) {
  return (
    <div className="flex gap-1 overflow-x-auto rounded-xl bg-gray-100 p-1">
      {options.map((option) => (
        <button
          key={String(option.key)}
          type="button"
          onClick={() => onSelect(option.key)}
          className={`shrink-0 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
            selected === option.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
