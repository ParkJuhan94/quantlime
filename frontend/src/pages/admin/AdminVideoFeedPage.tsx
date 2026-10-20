import { useState } from 'react'
import {
  cleanupVideos,
  collectVideos,
  initializeVelocity,
  republishSummarize,
  republishTranscribe,
} from '../../api/admin'
import { AdminActionCard } from '../../components/admin/AdminActionCard'
import { AdminChannelList } from '../../components/admin/AdminChannelList'
import { CollectResultTable } from '../../components/admin/CollectResultTable'
import { useAdminVideoChannelsQuery } from '../../hooks/queries/useAdminChannels'
import { describeAdminError } from '../../utils/adminError'

// 채널 한 줄의 "속도 초기화" - 최근 30개 업로드의 views/hours 중앙값을 다시 계산해 저장한다.
function VelocityButton({ channelId, onDone }: { channelId: number; onDone: () => void }) {
  const [state, setState] = useState<{ kind: 'idle' | 'running' } | { kind: 'done' | 'error'; text: string }>({
    kind: 'idle',
  })

  async function handleClick() {
    setState({ kind: 'running' })
    try {
      const velocity = await initializeVelocity(channelId)
      setState({ kind: 'done', text: `중앙값 ${velocity}` })
      onDone()
    } catch (error) {
      setState({ kind: 'error', text: describeAdminError(error) })
    }
  }

  return (
    <div className="flex items-center gap-2">
      {(state.kind === 'done' || state.kind === 'error') && (
        <span className={`text-xs ${state.kind === 'error' ? 'text-red-600' : 'text-gray-500'}`}>{state.text}</span>
      )}
      <button
        type="button"
        onClick={() => void handleClick()}
        disabled={state.kind === 'running'}
        className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
      >
        {state.kind === 'running' ? '계산 중…' : '속도 초기화'}
      </button>
    </div>
  )
}

export function AdminVideoFeedPage() {
  const channelsQuery = useAdminVideoChannelsQuery()
  const refreshChannels = () => void channelsQuery.refetch()

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-base font-bold text-gray-900">영상 수집</h2>
        <p className="text-sm text-gray-500">
          유튜브 채널의 새 영상을 모아 자막과 AI 요약까지 처리하는 파이프라인을 수동으로 돌립니다.
        </p>
      </div>

      <AdminChannelList
        title="채널"
        channels={channelsQuery.data}
        isLoading={channelsQuery.isLoading}
        isError={channelsQuery.isError}
        renderAction={(channel) => <VelocityButton channelId={channel.channelId} onDone={refreshChannels} />}
      />

      <AdminActionCard
        title="전체 채널 영상 수집"
        description="채널별 수집, 적재, 필터링을 지금 실행합니다. 정해진 시각의 스케줄러가 이미 돌고 있으면 거절돼요."
        buttonLabel="수집 실행"
        run={async () => {
          const results = await collectVideos()
          refreshChannels()
          return results
        }}
        renderResult={(results) => <CollectResultTable results={results} />}
      />

      <AdminActionCard
        title="자막 수집 이벤트 재발행"
        description="자막 수집 대기 영상(선택됨, 재시도 한도 안의 실패)에 이벤트를 다시 보냅니다. 응답은 처리 결과가 아니라 보낸 건수이고, 실제 처리는 뒤에서 비동기로 진행돼요."
        buttonLabel="재발행"
        run={republishTranscribe}
        renderResult={(count) => <p>{count}건 발행했어요.</p>}
      />

      <AdminActionCard
        title="AI 요약 이벤트 재발행"
        description="자막이 끝난 영상(재시도 한도 안의 실패 포함)에 요약 이벤트를 다시 보냅니다. 응답은 보낸 건수이고, 실제 요약은 비동기로 진행돼요."
        buttonLabel="재발행"
        confirm={{
          title: 'AI 요약 이벤트를 재발행할까요?',
          description:
            '요약 대상이 많으면 Gemini 호출 쿼터를 많이 쓰게 돼요. 하루 쿼터는 텔레그램 다이제스트와 함께 씁니다.',
        }}
        run={republishSummarize}
        renderResult={(count) => <p>{count}건 발행했어요.</p>}
      />

      <AdminActionCard
        title="보존 기간 지난 영상 정리"
        description="발행일이 14일을 넘은 영상과 그 자막, 요약, 종목 태그를 지금 삭제합니다. 정해진 시각의 스케줄러가 돌고 있으면 거절돼요."
        buttonLabel="영구 삭제"
        tone="danger"
        confirm={{
          title: '14일이 지난 영상 데이터를 삭제할까요?',
          description: '영상, 자막, 요약, 종목 태그가 함께 삭제되고 되돌릴 수 없어요.',
        }}
        run={cleanupVideos}
        renderResult={(count) => <p>{count}건 삭제했어요.</p>}
      />

      <p className="rounded-xl bg-white px-4 py-3 text-sm text-gray-500">
        자막 수동 임포트는 화면에 두지 않았어요. 운영 서버 IP를 유튜브가 막아 자막을 직접 못 가져올 때는, 로컬에서{' '}
        <code className="rounded bg-gray-100 px-1 py-0.5 text-xs text-gray-700">
          scripts/sync-transcripts-to-prod.sh
        </code>
        를 실행해 로컬에 수집한 자막을 운영으로 올리세요.
      </p>
    </div>
  )
}
