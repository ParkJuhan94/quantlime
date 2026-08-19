// 커스텀 지표. 엔드포인트별 임계값이나 별도 분석이 필요한 것만 여기 둔다 -
// 나머지는 k6 내장 http_req_duration/http_req_failed로 충분하다.
import { Trend, Rate, Counter } from 'k6/metrics';

export const durByEndpoint = new Trend('ql_endpoint_duration', true);
export const serverErrors = new Rate('ql_server_error_rate'); // 5xx 비율(4xx 제외)

export const wsConnected = new Counter('ql_ws_connected');
export const wsMessages = new Counter('ql_ws_messages');
export const wsFirstMsgLatency = new Trend('ql_ws_first_message_ms', true);
