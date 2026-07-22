# QuantLime Frontend

국내 주식 기술적 지표 스코어링 + 관심 종목 실시간 모니터링 프론트엔드.
React 19 + TypeScript + Vite + Tailwind CSS.

## 실행

```bash
npm install
cp .env.example .env.local   # VITE_API_BASE_URL, OAuth 클라이언트 ID 등 채우기
npm run dev                  # http://localhost:3001
```

개발 서버 포트는 **3001로 고정**돼 있다(`vite.config.ts`의
`strictPort`). OAuth 리다이렉트 URI는 프론트가 `window.location.origin`
기준으로 런타임에 동적 생성해 백엔드로 넘기므로, 각 프로바이더 콘솔에
등록된 Authorized Redirect URI가 이 포트를 전제로 한다 - 임의로 바꾸면
로그인이 깨진다.

## 실제 소셜 로그인 없이 개발하기

Google/Kakao/Naver OAuth 앱을 프로바이더 콘솔에 등록하지 않았다면,
로그인 화면의 "개발용 로그인" 버튼(개발 모드에서만 노출)으로 백엔드의
`POST /dev/auth/token`(개발 프로필 전용)을 호출해 실제 로그인 없이
인증이 필요한 화면을 전부 확인할 수 있다. 백엔드가 `dev` 프로필로
기동돼 있어야 한다.

## 백엔드 CORS

백엔드가 이 프론트엔드 오리진(`http://localhost:3001`)을 허용하도록
CORS가 설정돼 있다(`backend/api/.../auth/config/SecurityConfig.java`).
오리진을 바꾸려면 백엔드 `.env`의 `APP_CORS_ALLOWED_ORIGIN`도 함께 바꿔야 한다.

## 빌드

```bash
npm run build   # tsc -b && vite build
```

## 디렉터리 구조

```
src/
├── main.tsx / App.tsx      # 진입점, 라우팅, 프로바이더 배선
├── pages/                   # 화면 단위 컴포넌트
├── components/              # layout/common/watchlist/search/chart/score
├── hooks/                    # useStockPriceSocket, queries/*(React Query)
├── realtime/stompClient.ts  # STOMP 클라이언트 싱글턴(구독 참조 카운팅)
├── auth/                     # AuthContext, tokenStorage, ProtectedRoute
├── api/                      # axios 클라이언트 + 엔드포인트별 래퍼
├── types/                    # 백엔드 DTO 미러링
└── config/                   # env.ts, oauth.ts(authorize URL 빌더)
```
