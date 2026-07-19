import { useState } from 'react'
import { useCreateWatchlistGroup } from '../../hooks/queries/useWatchlistGroups'
import { useAddWatchlist } from '../../hooks/queries/useWatchlist'
import { GroupNameDialog } from './GroupNameDialog'
import type { WatchlistGroupResponse } from '../../types/watchlist'

interface AddToWatchlistGroupPickerProps {
  stockCode: string
  groups: WatchlistGroupResponse[]
  onClose: () => void
}

// 관심종목은 등록과 동시에 반드시 그룹에 속해야 한다("미분류" 폐지) -
// 하트 아이콘 등으로 처음 등록을 시도하면 그룹을 고르게 하고, 그룹이
// 하나도 없으면 그룹 생성 다이얼로그를 바로 띄운다.
export function AddToWatchlistGroupPicker({ stockCode, groups, onClose }: AddToWatchlistGroupPickerProps) {
  const [creatingGroup, setCreatingGroup] = useState(groups.length === 0)
  const addWatchlist = useAddWatchlist()
  const createGroup = useCreateWatchlistGroup()

  async function handlePick(groupId: number) {
    await addWatchlist.mutateAsync({ stockCode, groupId })
    onClose()
  }

  async function handleCreateAndPick(name: string) {
    const group = await createGroup.mutateAsync(name)
    await addWatchlist.mutateAsync({ stockCode, groupId: group.id })
    onClose()
  }

  if (creatingGroup) {
    return (
      <GroupNameDialog
        open
        title={groups.length === 0 ? '먼저 그룹을 만들어주세요' : '그룹 추가하기'}
        submitLabel="추가하고 등록"
        onClose={() => (groups.length === 0 ? onClose() : setCreatingGroup(false))}
        onSubmit={(name) => void handleCreateAndPick(name)}
      />
    )
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-xs rounded-2xl bg-white p-5 shadow-2xl"
      >
        <p className="mb-3 text-sm font-semibold text-gray-900">어느 그룹에 추가할까요?</p>
        <div className="mb-3 flex max-h-64 flex-col gap-1 overflow-y-auto">
          {groups.map((group) => (
            <button
              key={group.id}
              type="button"
              onClick={() => void handlePick(group.id)}
              className="rounded-lg px-3 py-2.5 text-left text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              {group.name}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => setCreatingGroup(true)}
          className="mb-2 w-full rounded-lg border border-gray-200 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        >
          + 새 그룹 만들기
        </button>
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
