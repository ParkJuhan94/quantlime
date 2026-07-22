import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { env } from '../config/env'
import { useMySubscriptionQuery, useSubscriptionPlansQuery } from '../hooks/queries/useSubscription'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { getErrorMessage } from '../api/errors'

// 3개월 플랜(23,700원)은 금액이 낮아 카드사 할부 승인이 거절될 수 있어
// 할부 선택 UI 자체를 숨긴다(6개월/12개월 플랜만 노출) - CLAUDE.md
// 결제 계획 §할부 참고.
const MIN_INSTALLMENT_ELIGIBLE_MONTHS = 6
const INSTALLMENT_OPTIONS = [0, 2, 3, 6, 12]
const PENDING_CHECKOUT_KEY = 'quantlime:pendingSubscriptionCheckout'

export function SubscribePage() {
  const plansQuery = useSubscriptionPlansQuery()
  const meQuery = useMySubscriptionQuery(true)
  const navigate = useNavigate()
  const [selectedPlanCode, setSelectedPlanCode] = useState<string | null>(null)
  const [installmentMonths, setInstallmentMonths] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (plansQuery.isLoading || meQuery.isLoading) return <LoadingSpinner />
  if (plansQuery.isError) {
    return <ErrorState message={getErrorMessage(plansQuery.error, '구독 플랜을 불러오지 못했어요')} />
  }

  const plans = plansQuery.data ?? []
  const customerKey = meQuery.data?.customerKey
  const alreadySubscribed = meQuery.data?.subscription?.status === '구독중'
  const selectedPlan = plans.find((p) => p.code === selectedPlanCode) ?? plans[0] ?? null
  const installmentEligible = (selectedPlan?.billingPeriodMonths ?? 0) >= MIN_INSTALLMENT_ELIGIBLE_MONTHS

  function handleSelectPlan(planCode: string, billingPeriodMonths: number) {
    setSelectedPlanCode(planCode)
    if (billingPeriodMonths < MIN_INSTALLMENT_ELIGIBLE_MONTHS) {
      setInstallmentMonths(0)
    }
  }

  async function handleSubscribe() {
    if (!selectedPlan || !customerKey) return
    setSubmitting(true)
    setError(null)
    try {
      // Toss 결제창은 전체 페이지 리다이렉트라 React 상태가 유실된다 -
      // 콜백 페이지(PaymentResultPage)가 이어받을 수 있게 세션스토리지에 남겨둔다.
      sessionStorage.setItem(
        PENDING_CHECKOUT_KEY,
        JSON.stringify({ planCode: selectedPlan.code, installmentMonths }),
      )
      const tossPayments = await loadTossPayments(env.tossPaymentsClientKey)
      const payment = tossPayments.payment({ customerKey })
      await payment.requestBillingAuth({
        method: 'CARD',
        successUrl: `${window.location.origin}/subscribe/result`,
        failUrl: `${window.location.origin}/subscribe/result`,
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : '카드 등록을 시작하지 못했어요')
      setSubmitting(false)
    }
  }

  if (alreadySubscribed) {
    return (
      <div className="mx-auto max-w-md text-center">
        <h1 className="mb-2 text-lg font-semibold text-gray-900">이미 구독중이에요</h1>
        <p className="mb-6 text-sm text-gray-500">구독 관리는 내 정보 페이지에서 확인할 수 있어요.</p>
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

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-1 text-lg font-semibold text-gray-900">프리미엄 구독</h1>
      <p className="mb-6 text-sm text-gray-500">
        카드 자동결제로 구독을 시작해요. 만료 전 자동으로 갱신되고, 자동갱신은 내 정보에서 언제든 끌 수 있어요.
      </p>

      <div className="flex flex-col gap-2">
        {plans.map((plan) => {
          const selected = selectedPlan?.code === plan.code
          const monthlyEquivalent = Math.round(plan.priceWon / plan.billingPeriodMonths)
          return (
            <button
              key={plan.code}
              type="button"
              onClick={() => handleSelectPlan(plan.code, plan.billingPeriodMonths)}
              className={`rounded-xl border px-4 py-3 text-left transition ${
                selected ? 'border-gray-900 bg-gray-100' : 'border-gray-200 hover:bg-gray-50'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-gray-900">{plan.name}</span>
                <span className="text-sm font-semibold text-gray-900">{plan.priceWon.toLocaleString()}원</span>
              </div>
              <p className="mt-1 text-xs text-gray-500">
                {plan.billingPeriodMonths}개월마다 자동 결제 · 월 {monthlyEquivalent.toLocaleString()}원 꼴
              </p>
            </button>
          )
        })}
      </div>

      {selectedPlan && installmentEligible && (
        <div className="mt-6">
          <p className="mb-2 text-xs font-medium text-gray-700">할부 선택</p>
          <div className="flex flex-wrap gap-2">
            {INSTALLMENT_OPTIONS.map((months) => (
              <button
                key={months}
                type="button"
                onClick={() => setInstallmentMonths(months)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  installmentMonths === months
                    ? 'border-gray-900 bg-gray-900 text-white'
                    : 'border-gray-200 text-gray-700 hover:bg-gray-50'
                }`}
              >
                {months === 0 ? '일시불' : `${months}개월`}
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-gray-400">일부 카드사는 결제 금액에 따라 할부가 제한될 수 있어요.</p>
        </div>
      )}

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <button
        type="button"
        disabled={!selectedPlan || !customerKey || submitting}
        onClick={handleSubscribe}
        className="mt-6 w-full rounded-lg bg-gray-900 py-2.5 text-sm font-semibold text-white transition hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-30"
      >
        {submitting ? '카드 등록창을 여는 중...' : '카드 등록하고 구독 시작'}
      </button>
    </div>
  )
}
