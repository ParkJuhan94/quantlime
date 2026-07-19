# 멀티스테이지 빌드 - 빌더 이미지에만 JDK+Gradle 캐시가 남고,
# 런타임 이미지는 JRE + jar 하나만 담아 이미지 크기를 줄인다.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 레이어 캐시: 소스 복사 전에 gradle 설정 파일만 먼저 복사해
# build.gradle이 안 바뀌면 이후 소스만 바뀐 재빌드에서 의존성 다운로드를
# 다시 하지 않도록 한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY api/build.gradle api/build.gradle
COPY core/build.gradle core/build.gradle
COPY common/build.gradle common/build.gradle
COPY event/build.gradle event/build.gradle
RUN ./gradlew --version

COPY api api
COPY core core
COPY common common
COPY event event
RUN ./gradlew :api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# bootJar만 매칭시키기 위해 *-SNAPSHOT.jar 글롭을 쓴다(plain jar는
# spring-projects 컨벤션상 *-plain.jar로 끝나 자동으로 제외됨).
COPY --from=builder /workspace/api/build/libs/*-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
# 컨테이너 베이스 이미지의 JVM 기본 타임존은 UTC다. 스케줄러(@Scheduled)는
# zone="Asia/Seoul"을 명시해 안전하지만, 등락률 계산 경로(PreviousCloseCache,
# DailyPriceService 등)의 LocalDate.now()는 그렇지 않아 00:00~08:59 KST
# 구간(=UTC 기준 전날)에 날짜 경계가 하루 밀려 전전일 종가를 전일 종가로
# 잘못 쓰는 문제가 있었다(2026-07-15 세션에서 로컬 개발환경 재현 중 발견 -
# 로컬 macOS는 OS 타임존이 이미 KST라 증상이 안 보였지만, TZ 미설정
# 컨테이너에서는 실제로 발생). 근본 수정은 모든 날짜 경계 로직을
# Asia/Seoul 기준으로 명시하는 것이지만, 컨테이너 자체의 기본 타임존을
# 맞춰두면 향후 유사한 실수(새 코드에서 LocalDate.now() 사용)도 한 번에
# 방어된다.
ENV TZ=Asia/Seoul
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
