import { useNavigate } from 'react-router-dom'
import { useMeQuery } from '../hooks/queries/useMe'
import { ProfileAvatar } from '../components/common/ProfileAvatar'
import {
  useCancelSubscription,
  useMySubscriptionQuery,
  usePaymentHistoryQuery,
} from '../hooks/queries/useSubscription'

export function MyInfoPage() {
  const meQuery = useMeQuery(true)
  const me = meQuery.data
  const navigate = useNavigate()

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
