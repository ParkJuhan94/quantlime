export interface SubscriptionPlanResponse {
  code: string
  name: string
  billingPeriodMonths: number
  priceWon: number
}

export interface SubscriptionResponse {
  planCode: string
  planName: string
  status: string
  currentPeriodStart: string
  currentPeriodEnd: string
  nextBillingAt: string | null
  autoRenew: boolean
  installmentMonths: number
}

// subscription은 아직 구독 이력이 없는 사용자면 null - customerKey는
// 구독 여부와 무관하게 항상 내려온다(카드 등록 위젯을 열기 전에 필요).
export interface SubscriptionMeResponse {
  customerKey: string
  subscription: SubscriptionResponse | null
}

export interface PaymentResponse {
  orderId: string
  amount: number
  installmentMonths: number
  status: string
  renewal: boolean
  approvedAt: string | null
  createdAt: string
}

// installmentMonths: 0=일시불, 2~12=할부개월
export interface IssueBillingKeyRequest {
  authKey: string
  planCode: string
  installmentMonths: number
}
