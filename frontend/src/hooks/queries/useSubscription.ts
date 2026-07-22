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
