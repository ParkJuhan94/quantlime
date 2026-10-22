import { Link } from 'react-router-dom'
import { extractExecutionId } from '../../utils/batchExecution'

// 배치 Job 시작 응답("... executionId=4")에 실행 상태 화면으로 가는 링크를 붙인다. AdminActionCard의 renderResult로 쓴다.
export function BatchStartedResult({ text }: { text: string }) {
  const executionId = extractExecutionId(text)
  return (
    <div className="flex flex-col gap-1">
      <p className="break-words">{text}</p>
      {executionId !== null && (
        <Link
          to={`/admin/batch?execution=${executionId}`}
          className="text-sm font-semibold text-gray-700 hover:underline"
        >
          실행 상태 보기
        </Link>
      )}
    </div>
  )
}
