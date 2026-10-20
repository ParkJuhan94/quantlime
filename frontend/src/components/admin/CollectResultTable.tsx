import type { CollectResult } from '../../types/admin'

// 채널별 수집 결과(성공/실패 사유)를 한눈에 보여준다.
export function CollectResultTable({ results }: { results: CollectResult[] }) {
  if (results.length === 0) return <p>수집할 채널이 없었어요.</p>
  const failed = results.filter((result) => !result.success).length
  return (
    <div>
      <p className="mb-2 font-semibold text-gray-900">
        채널 {results.length}개 중 {failed === 0 ? '모두 성공' : `${failed}개 실패`}
      </p>
      <ul className="divide-y divide-gray-200">
        {results.map((result) => (
          <li key={result.channelName} className="flex flex-wrap items-baseline justify-between gap-2 py-1.5">
            <span className="break-words font-medium text-gray-900">{result.channelName}</span>
            <span className={result.success ? 'text-gray-600' : 'text-red-600'}>
              {result.success ? `${result.discoveredCount}건 발견` : (result.errorMessage ?? '실패')}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
