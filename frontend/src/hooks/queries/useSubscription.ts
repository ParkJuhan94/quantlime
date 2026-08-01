import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelSubscription,
  getMySubscription,
  getPaymentHistory,
  getSubscriptionPlans,
  issueBillingKey,
} from '../../api/subscription'
import { queryKeys } from '../queryKeys'
import type { IssueBillingKeyRequest } from '../../types/subscription'
import { useAuth } from '../../auth/useAuth'

export function useSubscriptionPlansQuery() {
  return useQuery({
    queryKey: queryKeys.subscriptionPlans,
    queryFn: getSubscriptionPlans,
  })
}

export function useMySubscriptionQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.subscriptionMe,
    queryFn: getMySubscription,
    enabled,
  })
}

// 스코어/백테스트 등 구독자 전용 기능 게이팅에 쓴다. isResolving은 "로그인은
// 했지만 구독 여부를 아직 모르는" 찰나에 잠금 UI가 번쩍였다 사라지는 걸
// 막기 위한 것 - 이 구간엔 LoadingSpinner를 쓰고, 응답이 온 뒤에만
// isPremium으로 잠금/해제를 결정할 것.
export function useIsPremium() {
  const { isAuthenticated } = useAuth()
  const query = useMySubscriptionQuery(isAuthenticated)
  return {
    isPremium: query.data?.subscription?.status === '구독중',
    isResolving: isAuthenticated && query.isLoading,
  }
}

export function usePaymentHistoryQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.subscriptionPayments,
    queryFn: getPaymentHistory,
    enabled,
  })
}

export function useIssueBillingKey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: IssueBillingKeyRequest) => issueBillingKey(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.subscriptionMe })
      void queryClient.invalidateQueries({ queryKey: queryKeys.subscriptionPayments })
    },
  })
}

export function useCancelSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: cancelSubscription,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.subscriptionMe })
    },
  })
}
