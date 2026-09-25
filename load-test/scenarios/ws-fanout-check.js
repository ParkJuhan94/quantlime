// WebSocket(STOMP) fan-out 정합성/중복 배수 검증 - 로컬 scale-out
// (docker-compose.scaleout.yml) 전용. 용량 측정용 ws-stocks.js(0~3,000
// 커넥션 램프, EC2 2단계 벤치마크 전용)와는 목적이 달라 별도 파일로
// 분리했다 - 그대로 쓰면 로컬 3-컨테이너 정합성 확인에 과부하다
// (feat/realtime-fanout-scaleout, 2026-09-25).
//
// 측정 방법 - 문제 A(fan-out 깨짐)/문제 B(N배 중복)는 같은 시점에 동시
// 관측되지 않는다(SimpleBroker는 인스턴스별로 격리돼 중복이 우연히 안
// 보이고, relay로 바꾼 뒤에야 중복이 노출된다 - docs/00-sre/SRE.md §5
// 참고). 그래서 이 스크립트는 두 상황 모두에 쓸 수 있게 "종목코드별
// 수신 횟수"를 그대로 태깅해 남긴다:
//   - 각 VU가 수신한 MESSAGE 프레임을 destination 헤더에서 종목코드를
//     뽑아 stock_code 태그로 카운트한다(고정 50종목 풀 안이라 카디널리티
//     유계 - lib/metrics.js "카디널리티 큰 값 금지" 원칙에 안 걸림).
//   - 기대 수신 횟수(1배 기준, 종목 하나당) = floor(WS_HOLD_MS /
//     REALTIME_PRICE_POLL_INTERVAL_MS) - 서버의 실제 폴링 주기와 반드시
//     맞춰서 비교할 것(로컬 scale-out compose 기본값 3000ms).
//   - Run 1(SimpleBroker, 문제 A 재현): 커넥션별로 "구독한 종목 중
//     최소 1개라도 받았는가"(wsConnected 대비 wsMessages 유무)로 판단.
//   - Run 2/3(relay, 문제 B 재현/해소 확인): 종목코드당 실제 수신 횟수를
//     기대 수신 횟수로 나눈 배수로 판단(리더락 전 ~인스턴스 수배, 리더락
//     후 ~1.0배가 기대값).
//
// 실행 예(docker-compose.scaleout.yml의 nginx-ws, 기본 8088):
//   WS_BASE_URL=ws://localhost:8088 WS_HOLD_MS=30000 WS_CONNECTIONS=20 \
//     k6 run load-test/scenarios/ws-fanout-check.js
import ws from 'k6/ws';
import { check } from 'k6';
import { WS_BASE_URL, RUN_TAGS } from '../lib/config.js';
import { stockCodes, pickHot } from '../lib/data.js';
import { wsConnected, wsMessages, wsFirstMsgLatency } from '../lib/metrics.js';

const NULL_BYTE = ' ';
// ws-stocks.js의 기본 hold(120초)보다 훨씬 짧게 - 로컬 정합성 확인은
// 장중 여러 틱만 관측하면 충분하고, 길게 잡을수록 검증 한 번에 걸리는
// 시간만 늘어난다.
const HOLD_MS = Number(__ENV.WS_HOLD_MS || 30000);
const SUBS_PER_CONN = Number(__ENV.WS_SUBS || 3);
const CONNECTIONS = Number(__ENV.WS_CONNECTIONS || 20);

// ws-stocks.js와 동일한 STOMP 프레임 조립 로직 - 두 파일이 공유하기엔
// 너무 작은 순수 함수라(상태 없음) lib/로 추출하는 대신 그대로 복제했다
// (ws-stocks.js는 이번 작업에서 건드리지 않는다).
function frame(command, headers = {}, body = '') {
  const headerLines = Object.entries(headers)
    .map(([k, v]) => `${k}:${v}`)
    .join('\n');
  return `${command}\n${headerLines}\n\n${body}${NULL_BYTE}`;
}

// STOMP MESSAGE 프레임의 destination 헤더에서 종목코드를 뽑는다
// (destination:/topic/price.005930 형식 - WebSocketConfig/
// DomesticWatchlistPriceRelayScheduler 참고). \r?는 구현에 따라 섞일 수
// 있는 CRLF 방어.
function extractStockCode(messageFrame) {
  const match = messageFrame.match(/^destination:\/topic\/price\.(\S+?)\r?$/m);
  return match ? match[1] : 'unknown';
}

export const options = {
  scenarios: {
    fanoutCheck: {
      executor: 'constant-vus',
      vus: CONNECTIONS,
      // hold 시간 + 커넥션 셋업/정리 여유.
      duration: `${Math.ceil(HOLD_MS / 1000) + 15}s`,
    },
  },
  thresholds: {
    ws_connecting: [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '1m' }],
  },
};

export default function () {
  // 반드시 시딩된 관심종목 풀(50종목) 안에서만 고른다 - ws-stocks.js와
  // 동일한 이유(load-test/seed/seed-load-test-data.sql 참고).
  const codes = Array.from({ length: SUBS_PER_CONN }, () => pickHot(stockCodes, 50));
  const url = `${WS_BASE_URL}/ws/stocks/websocket`;
  let connectedAt = 0;
  let gotFirstMessage = false;

  const res = ws.connect(url, { tags: { ...RUN_TAGS, name: 'ws-fanout-check' } }, function (socket) {
    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0', host: 'quantlime' }));
    });

    socket.on('message', (msg) => {
      if (msg.startsWith('CONNECTED')) {
        connectedAt = Date.now();
        wsConnected.add(1);
        codes.forEach((code, i) =>
          socket.send(frame('SUBSCRIBE', { id: `sub-${i}`, destination: `/topic/price.${code}` }))
        );
      } else if (msg.startsWith('MESSAGE')) {
        wsMessages.add(1, { stock_code: extractStockCode(msg) });
        if (!gotFirstMessage) {
          gotFirstMessage = true;
          wsFirstMsgLatency.add(Date.now() - connectedAt);
        }
      } else if (msg.startsWith('ERROR')) {
        console.error(`STOMP ERROR 프레임: ${msg.slice(0, 200)}`);
      }
      // 그 외(빈 문자열/개행)는 하트비트라 무시.
    });

    socket.on('error', (e) => {
      if (e.error() !== 'websocket: close sent') {
        console.error(`ws error: ${e.error()}`);
      }
    });

    socket.setTimeout(() => {
      socket.send(frame('DISCONNECT', { receipt: 'bye' }));
      socket.close();
    }, HOLD_MS);
  });

  check(res, { 'ws 101 Switching Protocols': (r) => r && r.status === 101 });
}
