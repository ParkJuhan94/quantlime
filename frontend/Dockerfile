# 스테이지1: 정적 빌드. Vite는 빌드 시점에 import.meta.env.VITE_*를
# 번들에 인라인하므로 런타임이 아니라 빌드 인자로 주입해야 한다.
FROM node:22-alpine AS builder
WORKDIR /app

# 의존성 레이어 캐시: package.json/lock만 먼저 복사
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# nginx가 백엔드를 같은 오리진(/api, /ws)으로 프록시하므로 빈 문자열이면
# axios/STOMP가 상대경로로 요청 - CORS 자체가 필요 없어진다.
ARG VITE_API_BASE_URL=""
ARG VITE_GOOGLE_CLIENT_ID=""
ARG VITE_KAKAO_CLIENT_ID=""
ARG VITE_NAVER_CLIENT_ID=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
ENV VITE_KAKAO_CLIENT_ID=$VITE_KAKAO_CLIENT_ID
ENV VITE_NAVER_CLIENT_ID=$VITE_NAVER_CLIENT_ID
RUN npm run build

# 스테이지2: 정적 파일을 nginx로 서빙 + /api, /ws 리버스 프록시
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
