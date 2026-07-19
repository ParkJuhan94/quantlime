import { useEffect, useState } from 'react'
import { StockLogo } from '../common/StockLogo'
import { buildStockLogoUrl } from '../../utils/stockLogo'
import {
  useCreateWatchlistGroup,
  useDeleteWatchlistGroup,
  useRenameWatchlistGroup,
  useReorderWatchlistGroups,
} from '../../hooks/queries/useWatchlistGroups'
import { useMoveWatchlistGroup, useRemoveWatchlist, useReorderWatchlist } from '../../hooks/queries/useWatchlist'
import { GroupNameDialog } from './GroupNameDialog'
import { AddStockToGroupPopover } from './AddStockToGroupPopover'
import type { WatchlistGroupResponse, WatchlistResponse } from '../../types/watchlist'

interface WatchlistGroupEditModalProps {
  open: boolean
  onClose: () => void
  watchlist: WatchlistResponse[]
  groups: WatchlistGroupResponse[]
}

function GripIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="shrink-0 text-gray-300">
      <circle cx="9" cy="6" r="1.5" />
      <circle cx="15" cy="6" r="1.5" />
      <circle cx="9" cy="12" r="1.5" />
      <circle cx="15" cy="12" r="1.5" />
      <circle cx="9" cy="18" r="1.5" />
      <circle cx="15" cy="18" r="1.5" />
    </svg>
  )
}

// 관심 종목 편집 - 왼쪽은 그룹 목록(드래그로 순서 변경 + 오른쪽에서 종목을
// 드래그해 놓으면 그 그룹으로 이동), 오른쪽은 선택한 그룹에 속한 종목
// 목록(드래그로 순서 변경, 체크박스로 다중 선택 후 이동/삭제). 드래그
// 가능함을 알아보기 쉽도록 손잡이(⋮⋮) 아이콘과 cursor-grab/grabbing을
// 함께 쓴다. 관심 종목은 항상 어느 한 그룹에 속해야 하므로("미분류"
// 폐지) 여기엔 미분류 개념이 없다 - 그룹이 하나도 없으면 먼저 만들어야
// 한다.
export function WatchlistGroupEditModal({ open, onClose, watchlist, groups }: WatchlistGroupEditModalProps) {
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null)
  const [checkedCodes, setCheckedCodes] = useState<Set<string>>(new Set())
  const [nameDialog, setNameDialog] = useState<{ mode: 'create' | 'rename'; groupId?: number; initialName?: string } | null>(null)
  const [moveMenuOpen, setMoveMenuOpen] = useState(false)
  const [addStockOpen, setAddStockOpen] = useState(false)
  const [draggedGroupId, setDraggedGroupId] = useState<number | null>(null)
  const [draggedWatchlistItem, setDraggedWatchlistItem] = useState<{ id: number; stockCode: string } | null>(null)

  const createGroup = useCreateWatchlistGroup()
  const renameGroup = useRenameWatchlistGroup()
  const deleteGroup = useDeleteWatchlistGroup()
  const reorderGroups = useReorderWatchlistGroups()
  const moveWatchlistGroup = useMoveWatchlistGroup()
  const reorderWatchlist = useReorderWatchlist()
  const removeWatchlist = useRemoveWatchlist()

  useEffect(() => {
    if (open) {
      setSelectedGroupId(groups[0]?.id ?? null)
      setCheckedCodes(new Set())
    }
  }, [open, groups])

  useEffect(() => {
    if (!open) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  const stocksInSelectedGroup = watchlist
    .filter((item) => item.groupId === selectedGroupId)
    .sort((a, b) => a.sortOrder - b.sortOrder)

  function countInGroup(groupId: number): number {
    return watchlist.filter((item) => item.groupId === groupId).length
  }

  function toggleChecked(stockCode: string) {
    setCheckedCodes((prev) => {
      const next = new Set(prev)
      if (next.has(stockCode)) next.delete(stockCode)
      else next.add(stockCode)
      return next
    })
  }

  function toggleCheckedAll() {
    setCheckedCodes((prev) =>
      prev.size === stocksInSelectedGroup.length ? new Set() : new Set(stocksInSelectedGroup.map((s) => s.stockCode)),
    )
  }

  // --- 그룹 드래그 재정렬 ---
  function handleGroupDrop(targetGroupId: number) {
    if (draggedGroupId == null || draggedGroupId === targetGroupId) return
    const ids = groups.map((g) => g.id)
    const fromIndex = ids.indexOf(draggedGroupId)
    const toIndex = ids.indexOf(targetGroupId)
    if (fromIndex === -1 || toIndex === -1) return
    ids.splice(toIndex, 0, ids.splice(fromIndex, 1)[0])
    reorderGroups.mutate(ids)
    setDraggedGroupId(null)
  }

  // 오른쪽 종목 목록에서 드래그해 온 항목을 왼쪽 그룹 위에 놓으면 그
  // 그룹으로 이동시킨다(같은 그룹이면 아무 일도 하지 않음).
  function handleWatchlistDropOnGroup(targetGroupId: number) {
    if (draggedWatchlistItem == null) return
    if (targetGroupId !== selectedGroupId) {
      moveWatchlistGroup.mutate({ stockCode: draggedWatchlistItem.stockCode, groupId: targetGroupId })
    }
    setDraggedWatchlistItem(null)
  }

  // --- 종목 드래그 재정렬(같은 그룹 안에서만) ---
  function handleWatchlistReorderDrop(targetId: number) {
    if (draggedWatchlistItem == null || draggedWatchlistItem.id === targetId) return
    const ids = stocksInSelectedGroup.map((s) => s.id)
    const fromIndex = ids.indexOf(draggedWatchlistItem.id)
    const toIndex = ids.indexOf(targetId)
    if (fromIndex === -1 || toIndex === -1) return
    ids.splice(toIndex, 0, ids.splice(fromIndex, 1)[0])
    reorderWatchlist.mutate(ids)
    setDraggedWatchlistItem(null)
  }

  function handleMoveSelected(groupId: number) {
    checkedCodes.forEach((stockCode) => moveWatchlistGroup.mutate({ stockCode, groupId }))
    setCheckedCodes(new Set())
    setMoveMenuOpen(false)
  }

  function handleDeleteSelected() {
    if (checkedCodes.size === 0) return
    if (!window.confirm(`선택한 ${checkedCodes.size}개 종목을 관심 종목에서 삭제할까요?`)) return
    checkedCodes.forEach((stockCode) => removeWatchlist.mutate(stockCode))
    setCheckedCodes(new Set())
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="flex h-[560px] w-full max-w-2xl flex-col rounded-2xl bg-white p-5 shadow-2xl"
      >
        <div className="mb-4 flex items-center justify-between">
          <p className="text-base font-semibold text-gray-900">관심 종목 편집</p>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100"
          >
            ×
          </button>
        </div>

        <div className="flex min-h-0 flex-1 gap-4">
          {/* 왼쪽: 그룹 목록 */}
          <div className="flex w-44 shrink-0 flex-col">
            <button
              type="button"
              onClick={() => setNameDialog({ mode: 'create' })}
              className="mb-2 flex items-center gap-1 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50"
            >
              + 그룹 추가
            </button>
            <div className="flex-1 overflow-y-auto">
              {groups.length === 0 && (
                <p className="px-2 py-6 text-center text-xs text-gray-400">
                  아직 그룹이 없어요. 먼저 그룹을 만들어주세요.
                </p>
              )}
              {groups.map((group) => (
                <div
                  key={group.id}
                  draggable
                  onDragStart={() => setDraggedGroupId(group.id)}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={() =>
                    draggedWatchlistItem != null ? handleWatchlistDropOnGroup(group.id) : handleGroupDrop(group.id)
                  }
                  onClick={() => setSelectedGroupId(group.id)}
                  className={`group flex cursor-pointer items-center gap-1.5 rounded-lg px-2 py-2 text-sm ${
                    selectedGroupId === group.id ? 'bg-gray-100 font-semibold text-gray-900' : 'text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  <span className="cursor-grab active:cursor-grabbing">
                    <GripIcon />
                  </span>
                  <span className="min-w-0 flex-1 truncate">{group.name}</span>
                  <span className="text-xs text-gray-400">{countInGroup(group.id)}</span>
                  <button
                    type="button"
                    aria-label={`${group.name} 이름 변경`}
                    onClick={(event) => {
                      event.stopPropagation()
                      setNameDialog({ mode: 'rename', groupId: group.id, initialName: group.name })
                    }}
                    className="hidden text-gray-300 hover:text-gray-600 group-hover:inline"
                  >
                    ✎
                  </button>
                  <button
                    type="button"
                    aria-label={`${group.name} 삭제`}
                    onClick={(event) => {
                      event.stopPropagation()
                      if (window.confirm(`"${group.name}" 그룹을 삭제할까요? 종목은 기본 그룹으로 이동해요.`)) {
                        deleteGroup.mutate(group.id)
                        if (selectedGroupId === group.id) {
                          setSelectedGroupId(groups.find((g) => g.id !== group.id)?.id ?? null)
                        }
                      }
                    }}
                    className="hidden text-gray-300 hover:text-red-500 group-hover:inline"
                  >
                    🗑
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* 오른쪽: 선택한 그룹의 종목 목록 */}
          <div className="flex min-w-0 flex-1 flex-col border-l border-gray-100 pl-4">
            <div className="mb-2 flex items-center gap-2">
              <input
                type="checkbox"
                checked={checkedCodes.size > 0 && checkedCodes.size === stocksInSelectedGroup.length}
                onChange={toggleCheckedAll}
                disabled={selectedGroupId === null}
                className="h-3.5 w-3.5 rounded border-gray-300"
              />
              <div className="relative">
                <button
                  type="button"
                  disabled={checkedCodes.size === 0}
                  onClick={() => setMoveMenuOpen((prev) => !prev)}
                  className="rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-30"
                >
                  ⇄ 이동
                </button>
                {moveMenuOpen && (
                  <div className="absolute left-0 top-8 z-10 w-40 rounded-xl border border-gray-100 bg-white p-1.5 shadow-lg">
                    {groups
                      .filter((g) => g.id !== selectedGroupId)
                      .map((g) => (
                        <button
                          key={g.id}
                          type="button"
                          onClick={() => handleMoveSelected(g.id)}
                          className="w-full rounded-lg px-2.5 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
                        >
                          {g.name}
                        </button>
                      ))}
                  </div>
                )}
              </div>
              <button
                type="button"
                disabled={checkedCodes.size === 0}
                onClick={handleDeleteSelected}
                className="rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-30"
              >
                🗑 삭제
              </button>
              <button
                type="button"
                disabled={selectedGroupId === null}
                onClick={() => setAddStockOpen(true)}
                className="ml-auto rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-30"
              >
                + 종목 추가
              </button>
            </div>

            <div className="flex-1 overflow-y-auto">
              {selectedGroupId === null && (
                <p className="px-2 py-6 text-center text-sm text-gray-400">
                  왼쪽에서 그룹을 먼저 만들거나 선택해주세요.
                </p>
              )}
              {selectedGroupId !== null && stocksInSelectedGroup.length === 0 && (
                <p className="px-2 py-6 text-center text-sm text-gray-400">이 그룹에는 종목이 없어요.</p>
              )}
              {stocksInSelectedGroup.map((item) => (
                <div
                  key={item.id}
                  draggable
                  onDragStart={() => setDraggedWatchlistItem({ id: item.id, stockCode: item.stockCode })}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={() => handleWatchlistReorderDrop(item.id)}
                  className="flex items-center gap-2.5 rounded-lg px-2 py-2 hover:bg-gray-50"
                >
                  <input
                    type="checkbox"
                    checked={checkedCodes.has(item.stockCode)}
                    onChange={() => toggleChecked(item.stockCode)}
                    className="h-3.5 w-3.5 rounded border-gray-300"
                  />
                  <span className="cursor-grab active:cursor-grabbing">
                    <GripIcon />
                  </span>
                  <StockLogo logoUrl={buildStockLogoUrl(item.stockCode)} stockName={item.stockName} className="h-7 w-7" />
                  <span className="text-sm font-medium text-gray-900">{item.stockName}</span>
                  <span className="text-xs text-gray-400">{item.stockCode}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <GroupNameDialog
        open={nameDialog !== null}
        title={nameDialog?.mode === 'create' ? '그룹 추가하기' : '그룹 이름 변경'}
        initialName={nameDialog?.initialName}
        submitLabel={nameDialog?.mode === 'create' ? '추가' : '변경'}
        onClose={() => setNameDialog(null)}
        onSubmit={(name) => {
          if (nameDialog?.mode === 'create') {
            createGroup.mutate(name)
          } else if (nameDialog?.groupId != null) {
            renameGroup.mutate({ groupId: nameDialog.groupId, name })
          }
          setNameDialog(null)
        }}
      />

      {addStockOpen && selectedGroupId !== null && (
        <AddStockToGroupPopover
          groupId={selectedGroupId}
          watchlist={watchlist}
          onClose={() => setAddStockOpen(false)}
        />
      )}
    </div>
  )
}
