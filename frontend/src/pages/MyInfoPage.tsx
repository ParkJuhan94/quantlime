import { useNavigate } from 'react-router-dom'
import { buildAuthorizeUrl } from '../config/oauth'
import { useLinkedProvidersQuery, useUnlinkSocialAccount } from '../hooks/queries/useLinkedProviders'
import { useMeQuery } from '../hooks/queries/useMe'
import type { OAuthProviderName } from '../types/auth'
import { ProfileAvatar } from '../components/common/ProfileAvatar'
import { useCancelSubscription, useMySubscriptionQuery, usePaymentHistoryQuery } from '../hooks/queries/useSubscription'

const PROVIDERS: { key: OAuthProviderName; label: string }[] = [
  { key: 'google', label: '구글' },
  { key: 'kakao', label: '카카오' },
  { key: 'naver', label: '네이버' },
]

export function MyInfoPage() {
  const meQuery = useMeQuery(true)
  const me = meQuery.data
  const navigate = useNavigate()

  const linkedQuery = useLinkedProvidersQuery(true)
  const unlink = useUnlinkSocialAccount()
  const linkedByProvider = new Map((linkedQuery.data ?? []).map((item) => [item.provider, item]))

  const subscriptionQuery = useMySubscriptionQuery(true)
  const paymentHistoryQuery = usePaymentHistoryQuery(true)
  const cancelSubscription = useCancelSubscription()
  const subscription = subscriptionQuery.data?.subscription

  function handleCancel() {
    if (!window.confirm('자동갱신을 해지할까요? 현재 결제 기간이 끝날 때까지는 계속 이용할 수 있어요.')) {
      return
    }
    cancelSubscription.mutate()
  }

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

      {/* 이메일 일치만으로 계정을 합치지 않는다(탈취 벡터) - 로그인한 상태에서 추가 소셜 인증을 거쳐 직접 연결한다. */}
      <div className="mt-4 rounded-2xl border border-gray-100 bg-white p-6">
        <h2 className="mb-3 text-sm font-semibold text-gray-900">연결된 계정</h2>
        <ul className="flex flex-col gap-2">
          {PROVIDERS.map((provider) => {
            const linked = linkedByProvider.get(provider.key)
            return (
              <li key={provider.key} className="flex items-center justify-between text-sm">
                <span className="text-gray-700">
                  {provider.label}
                  {linked?.primary && <span className="ml-1.5 text-xs text-gray-400">가입 계정</span>}
                </span>
                {linked ? (
                  linked.primary ? (
                    <span className="text-xs text-gray-400">연결됨</span>
                  ) : (
                    <button
                      type="button"
                      disabled={unlink.isPending}
                      onClick={() => unlink.mutate(provider.key)}
                      className="rounded-lg border border-gray-200 px-3 py-1 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-30"
                    >
                      해제
                    </button>
                  )
                ) : (
                  <button
                    type="button"
                    disabled={linkedQuery.isLoading}
                    onClick={() => window.location.assign(buildAuthorizeUrl(provider.key, 'link'))}
                    className="rounded-lg bg-gray-900 px-3 py-1 text-xs font-semibold text-white hover:bg-gray-800 disabled:opacity-30"
                  >
                    연결
                  </button>
                )}
              </li>
            )
          })}
        </ul>
        {unlink.isError && <p className="mt-2 text-xs text-red-600">연결 해제에 실패했어요. 다시 시도해주세요.</p>}
      </div>

      <div className="mt-4 rounded-2xl border border-gray-100 bg-white p-6">
        <h2 className="mb-3 text-sm font-semibold text-gray-900">구독</h2>

        {subscriptionQuery.isLoading && <p className="text-sm text-gray-400">불러오는 중...</p>}

        {!subscriptionQuery.isLoading && !subscription && (
          <div className="flex items-center justify-between">
            <p className="text-sm text-gray-500">아직 구독중이 아니에요.</p>
            <button
              type="button"
              onClick={() => navigate('/subscribe')}
              className="rounded-lg bg-gray-900 px-3 py-1.5 text-sm font-semibold text-white hover:bg-gray-800"
            >
              구독하기
            </button>
          </div>
        )}

        {subscription && (
          <div>
            <div className="flex items-center justify-between">
              <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-900">
                {subscription.status}
              </span>
              <span className="text-sm font-semibold text-gray-900">{subscription.planName}</span>
            </div>
            <p className="mt-2 text-xs text-gray-500">
              {subscription.autoRenew
                ? `다음 결제일 ${subscription.nextBillingAt ?? '-'}`
                : `이용 종료일 ${subscription.currentPeriodEnd}(자동갱신 해지됨)`}
            </p>
            {subscription.autoRenew && subscription.status === '구독중' && (
              <button
                type="button"
                onClick={handleCancel}
                disabled={cancelSubscription.isPending}
                className="mt-3 rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-30"
              >
                자동갱신 해지
              </button>
            )}
          </div>
        )}

        {paymentHistoryQuery.data && paymentHistoryQuery.data.length > 0 && (
          <div className="mt-5 border-t border-gray-100 pt-4">
            <p className="mb-2 text-xs font-medium text-gray-500">결제 이력</p>
            <ul className="flex flex-col gap-2">
              {paymentHistoryQuery.data.map((payment) => (
                <li key={payment.orderId} className="flex items-center justify-between text-xs">
                  <span className="text-gray-500">
                    {payment.createdAt.slice(0, 10)} · {payment.status}
                    {payment.installmentMonths > 0 && ` · ${payment.installmentMonths}개월 할부`}
                  </span>
                  <span className="font-medium text-gray-900">{payment.amount.toLocaleString()}원</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  )
}
