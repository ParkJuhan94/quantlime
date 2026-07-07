import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { env } from '../config/env'
import type { PriceBroadcastMessage } from '../types/realtime'

type Listener = (message: PriceBroadcastMessage) => void

interface TopicEntry {
  listeners: Set<Listener>
  stompSubscription: StompSubscription | null
}

// 종목코드별로 리스너를 참조 카운팅한다 - 관심종목 페이지처럼 여러 행이
// 동시에 여러 종목을 구독해도 STOMP 클라이언트/연결은 앱 전체에 하나만
// 존재하고, 같은 종목을 구독하는 두 번째 리스너는 새 SUBSCRIBE 프레임을
// 만들지 않는다.
const topics = new Map<string, TopicEntry>()

const client = new Client({
  webSocketFactory: () => new SockJS(`${env.apiBaseUrl}/ws/stocks`),
  reconnectDelay: 5000,
  onConnect: () => {
    // 최초 연결 + 재연결 시, 현재 리스너가 남아있는 모든 토픽을 다시 구독한다.
    for (const [stockCode, entry] of topics) {
      if (!entry.stompSubscription) {
        entry.stompSubscription = subscribeStomp(stockCode, entry)
      }
    }
  },
})

function subscribeStomp(stockCode: string, entry: TopicEntry): StompSubscription {
  return client.subscribe(`/topic/price/${stockCode}`, (frame: IMessage) => {
    const message = JSON.parse(frame.body) as PriceBroadcastMessage
    entry.listeners.forEach((listener) => listener(message))
  })
}

/**
 * 종목 하나의 실시간 시세 구독. 반환된 함수를 호출하면 구독을 해제한다.
 * 같은 종목을 구독 중인 마지막 리스너가 해제될 때만 실제 STOMP 구독도 끊는다.
 */
export function subscribeStockPrice(stockCode: string, listener: Listener): () => void {
  if (!client.active) {
    client.activate()
  }

  let entry = topics.get(stockCode)
  if (!entry) {
    entry = { listeners: new Set(), stompSubscription: null }
    topics.set(stockCode, entry)
  }
  entry.listeners.add(listener)

  if (!entry.stompSubscription && client.connected) {
    entry.stompSubscription = subscribeStomp(stockCode, entry)
  }

  return () => {
    const current = topics.get(stockCode)
    if (!current) return
    current.listeners.delete(listener)
    if (current.listeners.size === 0) {
      current.stompSubscription?.unsubscribe()
      topics.delete(stockCode)
    }
  }
}
