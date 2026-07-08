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
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
