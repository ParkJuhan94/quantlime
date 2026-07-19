interface GroupQuickActionModalProps {
  groupName: string
  onClose: () => void
  onRename: () => void
  onAddStock: () => void
}

// 사이드패널에서 그룹 이름을 클릭했을 때 뜨는 작은 액션 모달 - 그룹
// 전체를 관리하는 WatchlistGroupEditModal까지 열 필요 없이 "이름 바꾸기"
// "이 그룹에 종목 추가하기" 두 액션만 빠르게 실행할 수 있게 한다.
export function GroupQuickActionModal({ groupName, onClose, onRename, onAddStock }: GroupQuickActionModalProps) {
  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-xs rounded-2xl bg-white p-5 shadow-2xl"
      >
        <p className="mb-3 truncate text-sm font-semibold text-gray-900">{groupName}</p>
        <div className="mb-2 flex flex-col gap-1">
          <button
            type="button"
            onClick={onAddStock}
            className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            <span className="text-gray-400">+</span> 이 그룹에 종목 추가
          </button>
          <button
            type="button"
            onClick={onRename}
            className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            <span className="text-gray-400">✎</span> 그룹 이름 변경
          </button>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="w-full rounded-lg border border-gray-200 py-2 text-sm font-semibold text-gray-500 hover:bg-gray-50"
        >
          닫기
        </button>
      </div>
    </div>
  )
}
