# QuantLime

국내 주식 기술적 지표 스코어링 + 관심 종목 실시간 모니터링 서비스.
주문 기능 없이 조회·분석에 집중한 개인 프로젝트입니다.

## 기술 스택

| 영역 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot 3.3.x (멀티모듈: api/core/common/event), Spring Data JPA, MySQL 8, Redis 7, WebSocket(STOMP) |
| Quant Engine | Python 3.11+, FastAPI, pandas/numpy/pandas-ta |
| Frontend | React 19 + TypeScript(Vite), Tailwind CSS v4, TradingView Lightweight Charts, React Query |
| 인프라 | Docker Compose(단일 EC2), GitHub Actions CI/CD, Prometheus/Grafana/Alertmanager |

## 디렉토리 구조

```
quantlime/
├── backend/        # Spring Boot 멀티모듈 (api, core, common, event)
├── quant-engine/   # Python FastAPI 퀀트 계산 서버 (지표·스코어링)
└── frontend/       # React + TypeScript + Vite
```

## 로컬 실행

```bash
# 인프라 (MySQL 3308, Redis 6381)
docker-compose up -d

# 백엔드
cd backend && ./gradlew :api:bootRun

# 퀀트 엔진
cd quant-engine && uvicorn main:app --reload --port 8000

# 프론트엔드
cd frontend && npm run dev
```

## 문서

- [`CLAUDE.md`](CLAUDE.md) — 프로젝트 구조·API 명세·코드 컨벤션·개발 Phase 현황 (핵심 참조 문서)
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — 로컬 개발 환경 실행법, 검증 방법론
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — EC2 배포 런북
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — 기능 확장 계획
- [`docs/CHANGELOG.md`](docs/CHANGELOG.md) — 세션별 작업 기록
