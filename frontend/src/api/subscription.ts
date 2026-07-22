import { apiClient } from './client'
import type {
  IssueBillingKeyRequest,
  PaymentResponse,
  SubscriptionMeResponse,
  SubscriptionPlanResponse,
  SubscriptionResponse,
} from '../types/subscription'

export async function getSubscriptionPlans(): Promise<SubscriptionPlanResponse[]> {
  const { data } = await apiClient.get<SubscriptionPlanResponse[]>('/api/subscription/plans')
  return data
}

export async function getMySubscription(): Promise<SubscriptionMeResponse> {
  const { data } = await apiClient.get<SubscriptionMeResponse>('/api/subscription/me')
  return data
}

export async function issueBillingKey(request: IssueBillingKeyRequest): Promise<SubscriptionResponse> {
  const { data } = await apiClient.post<SubscriptionResponse>('/api/subscription/billing-key', request)
  return data
}

export async function cancelSubscription(): Promise<void> {
  await apiClient.post('/api/subscription/cancel')
}

export async function getPaymentHistory(): Promise<PaymentResponse[]> {
  const { data } = await apiClient.get<PaymentResponse[]>('/api/subscription/payments')
  return data
}
