// 비구독 사용자에게 보여주는 백테스트 페이지 스켈레톤 - 실제 API를
// 호출하지 않으므로(useBacktestQuery의 enabled=false, PremiumGate 참고)
// 여기 나오는 수치는 전부 없다. 섹션 제목만 실제와 동일하게 두고 값이
// 들어갈 자리는 회색 블록으로 비워 "가짜 숫자를 만들지 않는다"는
// frontend/CLAUDE.md 원칙을 지킨다 - 이 컴포넌트는 항상 PremiumGate 안에서만
// 쓰여 블러+CTA로 덮인 상태로만 노출된다.
export function BacktestLockedPreview() {
  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-gray-200 bg-white p-4">
        <p className="mb-3 text-xs font-semibold text-gray-700">가격·스코어 추이</p>
        <div className="h-[280px] rounded-lg bg-gray-100" />
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4">
        <p className="mb-3 text-xs font-semibold text-gray-700">사분면 분포</p>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="rounded-lg bg-gray-100 p-3 text-center">
              <span className="inline-block h-3 w-10 rounded bg-gray-200" />
            </div>
          ))}
        </div>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4">
        <p className="mb-3 text-xs font-semibold text-gray-700">스코어 분포</p>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="h-32 rounded-lg bg-gray-100" />
          <div className="h-32 rounded-lg bg-gray-100" />
        </div>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3 flex items-center justify-between">
          <p className="text-xs font-semibold text-gray-700">Rank IC · 분위수 버킷</p>
          <div className="flex gap-1">
            <span className="inline-block h-6 w-14 rounded-lg bg-gray-100" />
            <span className="inline-block h-6 w-14 rounded-lg bg-gray-100" />
          </div>
        </div>
        <div className="h-40 rounded-lg bg-gray-100" />
      </div>
    </div>
  )
}
