.PHONY: run stop infra infra-down build clean kill load-seed load-clean load-smoke load-ramp load-journey

# 앱 실행
run:
	cd backend && ./gradlew :api:bootRun

# 8080 포트 프로세스 강제 종료
stop:
	@lsof -ti :8080 | xargs kill -9 2>/dev/null && echo "포트 8080 해제 완료" || echo "점유 프로세스 없음"

# 인프라 기동 (MySQL + Redis)
infra:
	docker-compose up -d

# 인프라 중지
infra-down:
	docker-compose down

# 빌드 (테스트 제외)
build:
	cd backend && ./gradlew build -x test

# 빌드 산출물 정리
clean:
	cd backend && ./gradlew clean

# 부하테스트 데이터 시딩(유저/관심종목/구독/텔레그램 다이제스트 + 토큰 발급)
# JWT_SECRET 환경변수 필요(backend/.env 참고). docs/LOAD_TESTING.md 참고.
load-seed:
	./load-test/seed/seed.sh

# 부하테스트 시딩 데이터 전량 제거
load-clean:
	./load-test/seed/seed.sh --teardown

# 스모크(1 VU, 각 엔드포인트 1회) - 부하테스트는 항상 이것부터
load-smoke:
	./load-test/run/run.sh smoke

# 엔드포인트별 한계 탐색 (예: make load-ramp TARGET=search)
load-ramp:
	./load-test/run/run.sh endpoint-ramp TARGET=$(TARGET)

# 혼합 여정 스트레스 - 시스템 전체의 무릎(knee) 탐색
load-journey:
	./load-test/run/run.sh journey
