import { Link, useParams } from 'react-router-dom'
import { useNoticeQuery } from '../hooks/queries/useNotices'
import { formatNoticeDate } from '../utils/noticeForm'

export function NoticeDetailPage() {
  const { noticeId } = useParams()
  const query = useNoticeQuery(Number(noticeId))
  const notice = query.data

  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/notices" className="mb-3 inline-block text-sm font-medium text-gray-500 hover:text-gray-700">
        ‹ 공지사항
      </Link>
      <article className="rounded-2xl border border-gray-100 bg-white p-6">
        {query.isLoading && <p className="py-10 text-center text-sm text-gray-400">불러오는 중…</p>}
        {query.isError && (
          // 삭제된 공지나 잘못된 주소(옛 알림이 가리키던 /notices/{id} 포함)
          <p role="alert" className="py-10 text-center text-sm text-gray-500">
            삭제됐거나 없는 공지예요.
          </p>
        )}
        {notice && (
          <>
            <h1 className="break-words text-lg font-bold text-gray-900">{notice.title}</h1>
            <p className="mt-1 text-xs text-gray-400">
              <time dateTime={notice.createdAt}>{formatNoticeDate(notice.createdAt)}</time>
              {notice.updatedAt !== notice.createdAt && ' · 수정됨'}
            </p>
            {/* 본문은 일반 텍스트다 - HTML로 해석하지 않고 줄바꿈만 유지한다(XSS 방지) */}
            <div className="mt-5 whitespace-pre-wrap break-words text-sm leading-relaxed text-gray-800">
              {notice.content}
            </div>
          </>
        )}
      </article>
    </div>
  )
}
