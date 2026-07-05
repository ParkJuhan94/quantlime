# QuantLab Phase 2 — 도메인 API 구현 계획

> 이 문서는 Phase 2 작업을 단계별로 쪼개서 진행하기 위한 참고 문서다.
> 실제 구현은 이 문서를 보며 Part 단위로 다시 계획-작업을 반복한다.

## Context

Phase 1(데이터 파이프라인: 토스증권 연동, 종목 마스터 적재, OHLCV 수집 스케줄러)이
완료·검증되어 `daily_price`에 실데이터가 쌓이는 상태다. 이제 이 데이터를 사용자에게
노출하는 **도메인 API 3종**을 구현한다 (CLAUDE.md §6·§8 Phase 2):

1. **종목 검색** — 종목명/코드로 검색 (완료, `feat: 종목 검색 API`)
2. **관심 종목 CRUD** — 등록/해제/목록 (완료, `feat: 관심 종목 CRUD API` 등,
   사용자별 스코핑 + 인증 필요)
3. **현재가 / 차트** — 실시간 현재가(토스 API) + 일봉 차트(DB `daily_price`)
   (완료, `feat: 현재가/차트 API` 등)

**Phase 2 도메인 API 3종 전부 완료.** 소셜 로그인(구글/카카오/네이버) + JWT 인증까지
포함해 Phase 2가 마무리됨. 실서버 기동 후 실제 토스 API 호출까지 포함한 curl
검증 완료 (차트: Phase 1에서 수집된 실데이터 반환, 현재가: 실시간 토스 API 응답 확인).

> **갱신 (인증 도입 이후):** Part B를 시작하기 전, 소셜 로그인(구글/카카오/네이버)
> + JWT 인증을 먼저 구현했다. 이유: 관심종목은 사용자별 데이터라 `Watchlist`가
> 처음부터 `user_id`로 스코핑되어야 나중의 스키마 리워크를 피할 수 있기 때문이다.
> 인증 구현 상세는 커밋 이력 참고 (`feat: User 도메인` ~ `test: 테스트 기반 구축
> 및 인증 테스트 작성`). 아래 Part B는 이 갱신을 반영해 다시 작성되었다 —
> **원래 계획에 있던 "단일 사용자, 인증 없음" 전제는 더 이상 유효하지 않다.**

인증/User 도메인은 존재하지 않고(포폴용 단일 사용자), CLAUDE.md §6 스펙의
watchlist URL에도 userId가 없으므로 그대로 단일 사용자 기준으로 구현한다.

또한 이번 Phase부터 CLAUDE.md §9.12 컨벤션대로 **풀 테스트**를 작성하기로 결정했다.
현재 테스트 기반이 전무하므로(빈 디렉터리 + 빌드 배선만 존재) 테스트 인프라
(TestContainers, 베이스 지원 클래스, Fixture, 태그 분류)부터 세운다.

---

## 확정된 설계 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 검색 파라미터 | `?q=` (스펙 준수) + `page`/`size` 확장 | CLAUDE.md §6 스펙, Slice 페이징 필요 |
| 차트 파라미터 | `?period=daily` + `?days=90` (`@Min(1)@Max(365)`) | 스펙 준수 + 조회기간 실용성 |
| 관심목록 조회 | JPQL `join fetch` (QueryDSL 미도입) | §9.8은 QueryDSL을 "복잡한 쿼리"에 한정, 단순 고정 조인은 YAGNI |
| 관심목록 응답 | `List<WatchlistResponse>` (Slice 아님) | 사용자 1인당 목록 규모가 작아 페이징 불필요 |
| 현재가 없음 처리 | 200 + `price=null` (404 아님, 신규 ErrorCode 없음) | 종목은 존재(검증 완료), "지금 시세 없음"은 not-found 아님 |
| 가격/차트 컨트롤러 | 신규 `PriceController` (base `/api/stocks`) | StockController에 price 도메인 의존 유입 방지 |
| 테스트 | §9.12 풀세트 (TestContainers MySQL, 지원 클래스, Fixture) | 사용자 선택 |

---

## Part A — 종목 검색 (기존 재사용, 최소 신규)

기존 `StockDetailResponse` / `StockMapper.toStockDetailResponse` / `PageResponse.of`
전부 재사용. 검색 메서드만 추가.

1. `core/.../stock/repository/StockRepository.java`
   `Slice<Stock> findByStockNameContainingIgnoreCaseOrStockCodeContaining(String name, String code, Pageable pageable)` 추가.
2. `core/.../stock/service/StockMasterService.java`
   `@Transactional(readOnly=true) Slice<Stock> searchStocks(String q, Pageable pageable)` 추가.
   `q.trim()` 후 name/code 양쪽에 동일 키워드 전달.
3. `api/.../stock/controller/StockController.java`
   `GET /search` 추가. `q`는 `@RequestParam @NotBlank`(+ 최소 길이 고려), `page`/`size`는
   `Pageable`. `slice.map(StockMapper::toStockDetailResponse)` → `PageResponse.of(...)` →
   `ResponseEntity<PageResponse<StockDetailResponse>>`. Swagger `@Operation` 부착.

**주의:** `PageResponse.of`는 `Slice<T>`에서 T가 이미 DTO여야 하므로 반드시 `.map()`
매핑 후 래핑 (`Slice<Stock>` 직접 전달 금지). 빈/공백 키워드는 `Containing("")`가
전체 매칭이므로 `@NotBlank`로 차단.

---

## Part B — 관심 종목 CRUD (사용자별 스코핑, 인증 필요)

> **인증 도입으로 갱신됨.** `User` 엔티티(`core/.../user/domain/User.java`),
> JWT 인증 필터, `@LoginUser` 리졸버(`api/.../auth/resolver/`)가 이미 구현되어
> 있으므로 그대로 재사용한다. 신규 feature 패키지 `com.quantlab.watchlist`.
> 엔티티는 `Stock.java` 패턴을 미러링하되 `User`와의 연관관계가 추가된다.

4. `core/.../watchlist/domain/Watchlist.java`
   `@Entity @Getter @NoArgsConstructor(PROTECTED)`, `TimeBaseEntity` 상속.
   `@ManyToOne(fetch=LAZY) @JoinColumn(name="user_id", foreignKey=@ForeignKey(NO_CONSTRAINT))`
   `User user` 필드 추가, `@ManyToOne(fetch=LAZY) @JoinColumn(name="stock_id", foreignKey=@ForeignKey(NO_CONSTRAINT))`
   `Stock stock` 필드. **unique 제약은 `(user_id, stock_id)` 복합**(같은 종목이라도
   사용자가 다르면 각자 등록 가능). private `@Builder` 생성자 +
   `validateWatchlist(Assert.notNull(user,...), Assert.notNull(stock,...))` + 정적 `of(User, Stock)`.
5. `core/.../watchlist/exception/WatchlistErrorCode.java`
   `ALREADY_EXISTS_WATCHLIST("...", "WL_000")`, `NOT_FOUND_WATCHLIST("...", "WL_001")`.
6. `core/.../watchlist/repository/WatchlistRepository.java` (`JpaRepository<Watchlist, Long>`)
   - `boolean existsByUser_IdAndStock_StockCode(Long userId, String stockCode)`
   - `Optional<Watchlist> findByUser_IdAndStock_StockCode(Long userId, String stockCode)`
   - `@Query("select w from Watchlist w join fetch w.stock where w.user.id = :userId order by w.createdAt desc") List<Watchlist> findAllWithStockByUserId(Long userId)`
7. `core/.../watchlist/dto/response/WatchlistResponse.java`
   record(id, stockCode, stockName, marketType label, sector, createdAt). (User 정보는
   응답에 노출하지 않음 — 요청자 본인 것만 조회되므로 불필요.)
8. `core/.../watchlist/dto/mapper/WatchlistMapper.java`
   `@NoArgsConstructor(PRIVATE)`, static `toWatchlistResponse(Watchlist)`.
9. `core/.../watchlist/service/WatchlistService.java`
   `StockMasterService` + `UserService` + `WatchlistRepository` 주입.
   - `addWatchlist(Long userId, String stockCode)`: `userService.getById(userId)` +
     `stockMasterService.getStockByCode(stockCode)`로 검증 →
     `existsByUser_IdAndStock_StockCode` 이면 `ValidationException(ALREADY_EXISTS_WATCHLIST)`
     → `save(Watchlist.of(user, stock))`. TOCTOU 방어로
     `DataIntegrityViolationException` catch → 동일 에러 변환(선택).
   - `removeWatchlist(Long userId, String stockCode)`: `findByUser_IdAndStock_StockCode`
     orElseThrow `NotFoundException(NOT_FOUND_WATCHLIST)` → delete.
   - `getWatchlist(Long userId)`: `findAllWithStockByUserId(userId)` → 매퍼
     (반드시 `@Transactional(readOnly=true)`, LAZY 프록시 접근이 세션 내에서 일어나도록).
10. `api/.../watchlist/controller/WatchlistController.java` (`@RequestMapping("/api/watchlist")`)
    - `GET` (`@LoginUser Long userId`) → `List<WatchlistResponse>` (200)
    - `POST /{stockCode}` (`@LoginUser Long userId`) → `201 Created` + `WatchlistResponse`
    - `DELETE /{stockCode}` (`@LoginUser Long userId`) → `204 No Content`
      (`ResponseEntity.noContent()`)
    - `SecurityConfig`의 `PERMIT_ALL_PATTERNS`에 `/api/watchlist/**`가 없으므로
      `anyRequest().authenticated()`에 의해 자동으로 인증이 필요해진다 (별도
      SecurityConfig 수정 불필요 — 이미 반영되어 있음).

**주의:** `Watchlist` 테스트용 Fixture(`WatchlistFixture`)는 `UserFixture.createUser()` +
`StockFixture`(신규 필요 시 추가)를 조합해 만든다.

---

## Part C — 현재가 / 차트 (신규, ErrorCode 변경 없음)

기존 `TossApiClient.getCurrentPrices(symbols)` + `DailyPriceService.getDailyPrices(code, start, end)`
재사용. 신규 코드 없음(PR_002 미도입).

11. `core/.../price/dto/response/CurrentPriceResponse.java`
    record(String stockCode, Long price, String currency, String timestamp). `price` nullable.
12. `core/.../price/dto/response/DailyChartResponse.java`
    record(LocalDate tradeDate, Long open, Long high, Long low, Long close, Long volume).
    날짜에 `@JsonFormat(pattern="yyyy-MM-dd", timezone="Asia/Seoul")` (§9.3).
13. `core/.../price/dto/mapper/PriceMapper.java`
    - `toCurrentPriceResponse(String stockCode, TossPriceResponse.TossPrice)` —
      `lastPrice` null/blank 가드 후 `Long.parseLong` (KRW 정수, `TossPriceMapper` 선례).
    - `toDailyChartResponse(DailyPrice)`.
14. `core/.../price/service/StockPriceService.java`
    `StockMasterService` + `TossApiClient` + `DailyPriceService` 주입.
    - `getCurrentPrice(stockCode)`: `getStockByCode` 검증 → `getCurrentPrices(stockCode)` →
      `result` 빈 리스트 가드(경고 로그 + `price=null`) → 첫 요소 매핑.
    - `getChart(stockCode, days)`: 검증 → `start=today.minusDays(days)`, `end=today` →
      `getDailyPrices` → `List<DailyChartResponse>` 매핑. 빈 결과는 200 + 빈 리스트.
15. `api/.../price/controller/PriceController.java` (`@RequestMapping("/api/stocks")`)
    - `GET /{stockCode}/price` → `CurrentPriceResponse`
    - `GET /{stockCode}/chart` → `List<DailyChartResponse>`.
      `period`(기본 `daily`, 현재 daily만 허용) + `days`(`@Min(1) @Max(365)`, 기본 90).

`GlobalExceptionHandler`, `PageResponse`, `PriceErrorCode`, `event` 모듈은 변경 없음.

---

## Part D — 테스트 기반 구축 + 테스트 (§9.12)

현재 테스트 전무 (확인 완료: `src/test/java`는 api/core에 빈 디렉터리만 존재,
`testFixtures`도 비어있음, TestContainers 의존성 없음, 지원 클래스 없음). 아래 순서로
기반부터 세운다.

**빌드 배선**
16. `backend/build.gradle` (subprojects 블록)
    - Testcontainers BOM + `testcontainers`, `testcontainers:mysql`, `testcontainers:junit-jupiter`
      `testImplementation` 추가.
    - `tasks.named('test'){ useJUnitPlatform() }` 유지 (태그는 분류 용도, 필요 시 그룹 태스크 추가는 후속).

**지원 클래스 (core)**
17. `core/src/testFixtures/.../support/TestContainerSupport.java`
    `abstract`, `@Testcontainers`, static `MySQLContainer`(8.x, reuse), `@DynamicPropertySource`로
    datasource url/user/pw 주입. (Redis는 Lettuce lazy connect이므로 컨테이너 불필요.)
18. `core/src/testFixtures/.../support/DataJpaTestSupport.java`
    `@DataJpaTest @AutoConfigureTestDatabase(replace=NONE)` extends `TestContainerSupport`.
19. `core/src/testFixtures/.../support/DatabaseCleaner.java` — 테이블 truncate 유틸.

**지원 클래스 (api)**
20. `api/src/test/.../support/ApiTestSupport.java`
    `@SpringBootTest @AutoConfigureMockMvc` extends `TestContainerSupport`, `MockMvc` 주입.
    price 테스트용으로 `@MockBean TossApiClient` 활용(토큰/Redis 우회).
21. `api/src/test/resources/application-test.yml` (또는 `application.yml`)
    test 프로파일, 스케줄러/ApplicationRunner 비활성화(CSV 적재 러너가 테스트에 개입하지
    않도록), 토스 크리덴셜 더미.

**Fixture**
22. `core/src/testFixtures/.../stock/StockFixture.java`, `.../watchlist/WatchlistFixture.java`
    `final class` + `@NoArgsConstructor(PRIVATE)`, 정적 팩토리.

**테스트 작성** (`@Tag`, `@DisplayName("[한국어]")`, Given-When-Then)
23. core `DataJpaTest`: `StockRepository` 검색, `WatchlistRepository`(join fetch/exists/find).
24. core 단위(Mockito): `WatchlistService`(중복 등록 방지·삭제·not-found), `PriceMapper`(null/blank 파싱).
25. api 통합: `StockController /search`, `WatchlistController` CRUD(201/204/400/404),
    `PriceController` price(빈 result→null)/chart(범위 검증) — `TossApiClient` MockBean.

---

## 커밋 계획 (granular, 컨벤션 준수)

작은 단위·한국어 컨벤션 메시지로 분리:

1. `chore: Phase 2 테스트 기반 구축 (TestContainers, 지원 클래스, Fixture)`
2. `feat: 종목 검색 API`
3. `feat: 관심 종목 도메인 및 CRUD API`
4. `feat: 현재가/일봉 차트 API`
5. `test: 종목/관심종목/시세 API 테스트` (또는 각 feat 커밋에 테스트 동봉 — 기능별 분리)

---

## 검증

```bash
# 인프라
docker-compose up -d

# 전체 테스트 (TestContainers가 MySQL 컨테이너 자동 기동)
cd backend && ./gradlew test

# 앱 실행 (dev 프로파일)
cd backend && ./gradlew :api:bootRun
```

Swagger(`http://localhost:8080/swagger-ui.html`) 또는 curl로 엔드투엔드 확인:

```bash
# 검색
curl 'http://localhost:8080/api/stocks/search?q=삼성&page=0&size=10'
# 관심종목 등록/목록/해제
curl -X POST http://localhost:8080/api/watchlist/005930
curl http://localhost:8080/api/watchlist
curl -X DELETE http://localhost:8080/api/watchlist/005930
# 현재가 / 차트
curl http://localhost:8080/api/stocks/005930/price
curl 'http://localhost:8080/api/stocks/005930/chart?period=daily&days=30'
```

기대: 검색은 `PageResponse` 구조, 관심종목 중복 등록 시 400 + `WL_000`, 미등록 해제
시 404 + `WL_001`, 존재하지 않는 종목 조회 시 404 + `ST_000`, 시세 없는 종목은
200 + `price=null`.

---

## 참고: 재사용 대상 (신규 작성 금지)

- `StockMasterService.getStockByCode()` — 모든 기능의 stockCode 존재 검증에 재사용
  (`core/.../stock/service/StockMasterService.java`)
- `StockDetailResponse` / `StockMapper.toStockDetailResponse` — 검색 결과 그대로 사용
- `PageResponse.of(Slice)` — 검색 페이징 응답 (`core/.../common/dto/PageResponse.java`)
- `TossApiClient.getCurrentPrices(symbols)` — 현재가 (`core/.../infra/toss/TossApiClient.java`)
- `DailyPriceService.getDailyPrices(code, start, end)` — 차트 (`core/.../price/service/DailyPriceService.java`)
- `Stock.java` 엔티티 패턴 — `Watchlist` 엔티티 미러링 대상
- `NotFoundException`/`ValidationException`(ErrorCode 생성자) — 예외 처리
- `UserService.getById(userId)` — Watchlist에서 요청자 검증에 재사용
  (`core/.../user/service/UserService.java`)
- `@LoginUser` + `LoginUserArgumentResolver` — 컨트롤러에서 인증된 userId 주입
  (`api/.../auth/resolver/`)
- `UserFixture.createUser()` — 테스트에서 사용자 픽스처 생성 (`core/src/testFixtures/.../user/UserFixture.java`)

---

## 진행 방식

이 문서는 한 번에 전부 구현하기 위한 것이 아니라, 다음 세션들에서 Part 단위(A → B → C → D,
또는 D를 먼저 세운 뒤 A/B/C)로 다시 짧게 계획하고 구현·커밋을 반복하기 위한 참고 자료다.
각 Part 착수 전 이 문서의 해당 섹션을 다시 검토하고, 실제 코드 상태와 어긋난 부분이 있으면
이 문서를 먼저 갱신한다.
