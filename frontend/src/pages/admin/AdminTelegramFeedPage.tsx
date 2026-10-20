import { cleanupTelegram, collectTelegram, republishDigest } from '../../api/admin'
import { AdminActionCard } from '../../components/admin/AdminActionCard'
import { AdminChannelList } from '../../components/admin/AdminChannelList'
import { CollectResultTable } from '../../components/admin/CollectResultTable'
import { useAdminTelegramChannelsQuery } from '../../hooks/queries/useAdminChannels'

export function AdminTelegramFeedPage() {
  const channelsQuery = useAdminTelegramChannelsQuery()
  const refreshChannels = () => void channelsQuery.refetch()

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-base font-bold text-gray-900">텔레그램 수집</h2>
        <p className="text-sm text-gray-500">
          텔레그램 채널 글을 모아 채널과 날짜 단위로 AI 다이제스트를 만드는 파이프라인을 수동으로 돌립니다.
        </p>
      </div>

      <AdminChannelList
        title="채널"
        channels={channelsQuery.data}
        isLoading={channelsQuery.isLoading}
        isError={channelsQuery.isError}
      />

      <AdminActionCard
        title="전체 텔레그램 채널 글 수집"
        description="채널별 수집, 적재, 필터링을 지금 실행합니다. 정해진 시각의 스케줄러가 이미 돌고 있으면 거절돼요."
        buttonLabel="수집 실행"
        run={async () => {
          const results = await collectTelegram()
          refreshChannels()
          return results
        }}
        renderResult={(results) => <CollectResultTable results={results} />}
      />

      <AdminActionCard
        title="AI 다이제스트 생성 이벤트 재발행"
        description="채널별 오늘자 다이제스트 생성 이벤트를 지금 다시 보냅니다. 응답은 보낸 건수이고, 실제 생성은 뒤에서 비동기로 진행돼요."
        buttonLabel="재발행"
        confirm={{
          title: '다이제스트 생성 이벤트를 재발행할까요?',
          description:
            'Gemini 호출 쿼터를 써요. 하루 쿼터는 유튜브 요약과 함께 쓰고, 다이제스트 재생성은 하루 3회까지가 기준이에요.',
        }}
        run={republishDigest}
        renderResult={(count) => <p>{count}건 발행했어요.</p>}
      />

      <AdminActionCard
        title="보존 기간 지난 텔레그램 데이터 정리"
        description="발행일이 14일을 넘은 글과 다이제스트, 종목 태그를 지금 삭제합니다. 정해진 시각의 스케줄러가 돌고 있으면 거절돼요."
        buttonLabel="영구 삭제"
        tone="danger"
        confirm={{
          title: '14일이 지난 텔레그램 데이터를 삭제할까요?',
          description: '글, 다이제스트, 종목 태그가 함께 삭제되고 되돌릴 수 없어요.',
        }}
        run={cleanupTelegram}
        renderResult={(result) => (
          <p>
            글 {result.deletedPostCount}건, 다이제스트 {result.deletedDigestCount}건을 삭제했어요.
          </p>
        )}
      />
    </div>
  )
}
