import type { PriceBroadcastMessage } from '../types/realtime'

// 소켓 훅은 마지막 메시지를 계속 들고 있어서, 푸시가 끊기면(장 마감, 재연결, 상위 10행에서 밀려남)
// 오래된 값이 더 새로운 REST 값을 덮는다. 이 시간이 지난 푸시는 버리고 REST로 돌아간다.
// 체결이 듬성한 종목은 운영 실측에서 20~40초씩 비지만(2026-11-05), 그동안 REST 값이 5초마다
// 들어오므로 표시가 멈추지는 않는다.
export const LIVE_PRICE_MAX_AGE_MS = 30_000

export function isLivePriceFresh(message: PriceBroadcastMessage | undefined, nowMs = Date.now()): boolean {
  if (!message) return false
  const pushedAt = Date.parse(message.timestamp)
  return !Number.isNaN(pushedAt) && nowMs - pushedAt <= LIVE_PRICE_MAX_AGE_MS
}
