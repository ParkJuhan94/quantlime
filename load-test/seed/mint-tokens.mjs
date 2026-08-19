#!/usr/bin/env node
// JWT를 오프라인으로 서명해 발급한다(Node 내장 crypto만 사용, 의존성 0).
//
// 로컬은 POST /dev/auth/token으로 토큰을 받을 수 있지만, EC2는 이 경로를
// 쓸 수 없다 - DevController가 @Profile("dev")라 prod 프로파일에서
// 로드되지 않고, 그 앞에서 nginx가 /dev를 SPA로 떨어뜨려 405로 먼저
// 막는다(docs/DEVELOPMENT.md 확인됨). 다행히 인증이 완전 스테이트리스
// (JwtAuthenticationFilter가 DB/Redis를 전혀 안 타고 userId/role을 토큰
// 클레임에서만 읽는다)라, backend의 JwtTokenProvider와 정확히 같은
// 클레임 구조로 HS256 서명하면 서버 코드 변경 없이 그대로 통과한다.
//
// 사용법:
//   JWT_SECRET=<backend .env의 JWT_SECRET> \
//     node load-test/seed/mint-tokens.mjs load-test/data/_user-list.json
//
// user-list.json 형식: [{ "userId": 1, "premium": true }, ...]
// (seed.sh가 DB에서 이 목록을 뽑아 넘겨준다)
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SECRET = process.env.JWT_SECRET;
if (!SECRET) {
  console.error('JWT_SECRET이 설정되지 않았다 - backend/.env의 값을 export할 것');
  process.exit(1);
}

const userListPath = process.argv[2];
if (!userListPath) {
  console.error('사용법: node mint-tokens.mjs <user-list.json>');
  process.exit(1);
}

const b64url = (buf) => Buffer.from(buf).toString('base64url');

// JwtTokenProvider.createToken과 동일한 클레임을 만든다:
//   sub  = userId(문자열)
//   type = "access"  - JwtAuthenticationFilter가 "refresh"면 거부한다
//   role = "USER"    - 없으면 인증은 되지만 권한이 없는 principal이 된다
//   iat/exp
// 서명키는 Keys.hmacShaKeyFor(secret.getBytes(UTF_8)) - 즉 시크릿 문자열의
// raw UTF-8 바이트를 그대로 키로 쓴다(base64 디코딩하지 않는다).
//
// 알고리즘은 HS256으로 고정한다. jjwt의 verifyWith(SecretKey)는 토큰
// 헤더에 선언된 alg를 기준으로 검증하므로, 백엔드가 자체 서명 시 실제로
// 어떤 HS*를 골랐는지와 무관하게, 여기서 HS256으로 서명한 토큰도 SECRET이
// HS256 최소 키 길이(32바이트) 이상이면 정상 검증된다(백엔드 JWT_SECRET은
// Keys.hmacShaKeyFor가 이미 그 이상을 요구하므로 항상 충족됨).
function mint(userId, ttlSec) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const payload = b64url(
    JSON.stringify({ sub: String(userId), type: 'access', role: 'USER', iat: now, exp: now + ttlSec })
  );
  const signature = crypto
    .createHmac('sha256', Buffer.from(SECRET, 'utf8'))
    .update(`${header}.${payload}`)
    .digest('base64url');
  return `${header}.${payload}.${signature}`;
}

const users = JSON.parse(fs.readFileSync(userListPath, 'utf8'));
// soak 테스트(45분+)를 감안해 기본 24시간. 백엔드 기본 액세스 토큰
// 유효시간(30분, jwt.access-token-validity)보다 훨씬 길게 잡는다 -
// 부하테스트 토큰은 만료 갱신 로직이 없으므로 애초에 넉넉히 발급한다.
const ttl = Number(process.env.TOKEN_TTL_SEC || 86400);

const outPath = path.join(__dirname, '..', 'data', 'tokens.json');
const tokens = users.map((u) => ({
  userId: u.userId,
  premium: !!u.premium,
  token: mint(u.userId, ttl),
}));
fs.writeFileSync(outPath, JSON.stringify(tokens, null, 2));
console.log(`토큰 ${tokens.length}개 발급 완료(TTL=${ttl}s, premium=${tokens.filter((t) => t.premium).length}개) → ${outPath}`);
