import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useIssueBillingKey } from '../hooks/queries/useSubscription'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { getErrorMessage } from '../api/errors'

const PENDING_CHECKOUT_KEY = 'quantlime:pendingSubscriptionCheckout'

interface PendingCheckout {
  planCode: string
  installmentMonths: number
}

// 토스 카드 등록 위젯의 successUrl/failUrl이 모두 이 페이지로 온다(둘 다
// 같은 경로) - authKey 쿼리파라미터 유무로 성공/실패를 구분한다.
export function PaymentResultPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const issueBillingKey = useIssueBillingKey()
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing')
  const [message, setMessage] = useState('')
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true

    const authKey = searchParams.get('authKey')
    const failureMessage = searchParams.get('message')
    const pendingRaw = sessionStorage.getItem(PENDING_CHECKOUT_KEY)
    sessionStorage.removeItem(PENDING_CHECKOUT_KEY)

    if (!authKey || !pendingRaw) {
      setStatus('error')
      setMessage(failureMessage ?? '카드 등록이 취소됐거나 실패했어요.')
      return
    }

    const pending = JSON.parse(pendingRaw) as PendingCheckout
    issueBillingKey.mutate(
      { authKey, planCode: pending.planCode, installmentMonths: pending.installmentMonths },
      {
        onSuccess: () => setStatus('success'),
        onError: (error) => {
          setStatus('error')
          setMessage(getErrorMessage(error, '결제에 실패했어요. 다시 시도해주세요.'))
        },
      },
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (status === 'processing') {
    return (
      <div className="mx-auto max-w-md py-16 text-center">
        <LoadingSpinner />
        <p className="mt-4 text-sm text-gray-500">결제를 진행하고 있어요...</p>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="mx-auto max-w-md py-16 text-center">
        <h1 className="mb-2 text-lg font-semibold text-gray-900">결제에 실패했어요</h1>
        <p className="mb-6 text-sm text-gray-500">{message}</p>
        <button
          type="button"
          onClick={() => navigate('/subscribe')}
          className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800"
        >
          다시 시도하기
        </button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="mb-2 text-lg font-semibold text-gray-900">구독이 시작됐어요</h1>
      <p className="mb-6 text-sm text-gray-500">다음 결제일에 자동으로 갱신돼요.</p>
      <button
        type="button"
        onClick={() => navigate('/me')}
        className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800"
      >
        내 정보로 이동
      </button>
    </div>
  )
}
