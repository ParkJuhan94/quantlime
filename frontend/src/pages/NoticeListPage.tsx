import { Link } from 'react-router-dom'
import { useNoticesInfiniteQuery } from '../hooks/queries/useNotices'
import { formatNoticeDate } from '../utils/noticeForm'

export function NoticeListPage() {
  const query = useNoticesInfiniteQuery()
  const notices = query.data?.pages.flatMap((page) => page.content) ?? []

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-4 text-lg font-bold text-gray-900">공지사항</h1>
      <div className="rounded-2xl border border-gray-100 bg-white">
        {query.isLoading && <p className="py-12 text-center text-sm text-gray-400">불러오는 중…</p>}
        {query.isError && (
          <p role="alert" className="py-12 text-center text-sm text-red-600">
            공지를 불러오지 못했어요. 새로고침해 주세요.
          </p>
        )}
        {!query.isLoading && !query.isError && notices.length === 0 && (
          <p className="py-12 text-center text-sm text-gray-400">아직 공지가 없어요.</p>
        )}
        {notices.length > 0 && (
          <ul className="divide-y divide-gray-100">
            {notices.map((notice) => (
              <li key={notice.id}>
                <Link to={`/notices/${notice.id}`} className="block px-5 py-4 transition hover:bg-gray-50">
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="break-words text-sm font-semibold text-gray-900">{notice.title}</span>
                    <time dateTime={notice.createdAt} className="shrink-0 text-xs text-gray-400">
                      {formatNoticeDate(notice.createdAt)}
                    </time>
                  </div>
                  <p className="mt-1 break-words text-xs text-gray-500">{notice.preview}</p>
                </Link>
              </li>
            ))}
          </ul>
        )}
        {query.hasNextPage && (
          <div className="border-t border-gray-100 p-3">
            <button
              type="button"
              onClick={() => void query.fetchNextPage()}
              disabled={query.isFetchingNextPage}
              className="w-full rounded-lg border border-gray-200 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              {query.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
