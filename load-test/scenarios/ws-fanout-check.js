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

// STOMP 프레임 종료 문자는 반드시 실제 NUL 바이트(0x00)여야 한다 - 공백
// 문자가 아니다. ws-stocks.js의 동일 상수는 파일에 raw NUL 바이트가
// 그대로 박혀 있는데(에디터에서는 빈 칸처럼 보임), 이 파일은 명시적
// \x00 이스케이프로 써서 같은 값을 명확하게 남긴다(2026-09-25 로컬
// 검증 중 발견 - raw NUL 바이트를 다시 타이핑하는 과정에서 공백
// 문자로 바뀌어 서버가 프레임 종료를 인식 못해 STOMP CONNECTED 응답
// 자체가 안 왔었다).
const NULL_BYTE = '\x00';
// ws-stocks.js의 기본 hold(120초)보다 훨씬 짧게 - 로컬 정합성 확인은
// 장중 여러 틱만 관측하면 충분하고, 길게 잡을수록 검증 한 번에 걸리는
// 시간만 늘어난다.
const HOLD_MS = Number(__ENV.WS_HOLD_MS || 30000);
const SUBS_PER_CONN = Number(__ENV.WS_SUBS || 3);
const CONNECTIONS = Number(__ENV.WS_CONNECTIONS || 20);
// seed-load-test-data.sql/dump-stock-codes.sql 관련 기존 인프라 주의사항
// 2건(둘 다 2026-09-25 로컬 검증 세션에서 실측으로 발견, 이번 작업 범위
// 밖의 기존 스크립트 동작이라 그 파일들은 고치지 않고 여기서 우회한다):
//   1) 주석은 "고정 50종목 풀"이라 적혀 있지만(@WATCHLIST_POOL), 실제
//      INSERT는 모든 유저에게 같은 @CODES_PER_USER(기본 10)개만 배정한다
//      (모든 유저가 동일 서브쿼리를 rn<=10으로 필터링해서 join하기 때문) -
//      실제로 관심종목에 걸리는 종목은 50개가 아니라 10개뿐이다.
//   2) dump-stock-codes.sql이 만드는 load-test/data/stock-codes.json은
//      "ORDER BY stock_code"가 붙은 서브쿼리를 JSON_ARRAYAGG로 감싸는데,
//      MySQL의 JSON_ARRAYAGG는 서브쿼리의 ORDER BY를 보존한다는 보장이
//      없다(실측 결과 stock_code 정렬이 아니라 사실상 임의 순서로
//      나왔다) - 그래서 이 배열의 "앞쪽 N개"가 watchlist 시딩이 실제로
//      고른 "앞쪽 N개"(ROW_NUMBER() OVER (ORDER BY stock_code)는
//      신뢰 가능)와 일치한다는 보장이 없다.
// 결과적으로 pickHot(stockCodes, N)만으로는 실제 관심종목에 정확히
// 맞는 코드를 뽑는다는 보장이 없다 - WS_CODES(콤마 구분 종목코드 목록)로
// 명시적으로 넘기면 이 무작위 추출 전체를 우회한다(로컬 scale-out
// 검증처럼 실제 시딩된 코드가 뭔지 미리 아는 상황에서 권장).
const POOL_SIZE = Number(__ENV.WS_POOL_SIZE || 50);
const EXPLICIT_CODES = __ENV.WS_CODES ? __ENV.WS_CODES.split(',').map((c) => c.trim()) : null;

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
  // WS_CODES가 있으면 그대로 쓰고(위 주석의 JSON_ARRAYAGG 순서 비보장
  // 문제를 우회), 없으면 기존처럼 무작위 풀에서 고른다 - 반드시 시딩된
  // 관심종목 풀 안에서만 고른다는 원칙은 ws-stocks.js와 동일
  // (load-test/seed/seed-load-test-data.sql 참고).
  const codes = EXPLICIT_CODES
    ? Array.from({ length: SUBS_PER_CONN }, (_, i) => EXPLICIT_CODES[i % EXPLICIT_CODES.length])
    : Array.from({ length: SUBS_PER_CONN }, () => pickHot(stockCodes, POOL_SIZE));
  const url = `${WS_BASE_URL}/ws/stocks/websocket`;
  let connectedAt = 0;
  let gotFirstMessage = false;

  const res = ws.connect(url, { tags: { ...RUN_TAGS, name: 'ws-fanout-check' } }, function (socket) {
    socket.on('open', () => {
      // host 헤더는 보내지 않는다 - ws-stocks.js의 동일 CONNECT 프레임
      // 주석 참고(RabbitMQ STOMP relay가 이 값을 AMQP 가상 호스트명으로
      // 써서 "Virtual host 'quantlime' access denied"로 거부한다,
      // 2026-09-25 로컬 검증 세션에서 실측).
      socket.send(frame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0' }));
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
