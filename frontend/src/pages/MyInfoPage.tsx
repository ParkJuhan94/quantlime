import { useMeQuery } from '../hooks/queries/useMe'
import { ProfileAvatar } from '../components/common/ProfileAvatar'

export function MyInfoPage() {
  const meQuery = useMeQuery(true)
  const me = meQuery.data

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-lg font-semibold text-gray-900">내 정보</h1>

      {meQuery.isLoading && <p className="text-sm text-gray-400">불러오는 중...</p>}

      {me && (
        <div className="flex flex-col items-center gap-4 rounded-2xl border border-gray-100 bg-white p-8">
          <ProfileAvatar
            profileImageUrl={me.profileImageUrl}
            nickname={me.nickname}
            className="h-20 w-20"
            textSizeClassName="text-2xl"
          />
          <div className="text-center">
            <p className="text-base font-semibold text-gray-900">{me.nickname}</p>
            {me.email && <p className="mt-1 text-sm text-gray-400">{me.email}</p>}
          </div>
        </div>
      )}
    </div>
  )
}
