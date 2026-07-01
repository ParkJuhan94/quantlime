.PHONY: run stop infra infra-down build clean kill

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
