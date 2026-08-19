-- k6 부하테스트용 시드 데이터.
--
-- ⚠️ 실행 전 필수 확인 (~/.claude/CLAUDE.md "DB/공유 상태를 건드리는 실험
-- 전 확인 원칙"): 이 스크립트가 실제로 어느 DB에 연결되는지 먼저 확인할
-- 것. TRUNCATE/DELETE는 teardown 스크립트에만 있고 이 파일엔 없다.
--
-- 전부 provider_id가 'loadtest-'로 시작해 teardown-load-test-data.sql이
-- 한 번에 식별해 지울 수 있다. INSERT IGNORE/존재 체크로 여러 번 실행해도
-- 안전(멱등)하다.
--
-- ⚠️ 구독 시딩 전 필수 확인: SUBSCRIPTION_BILLING_KEY_ENCRYPTION_KEY가
--    설정돼 있으면 BillingKeyConverter가 아래 평문 billing_key를 복호화
--    하려다 IllegalStateException을 던진다. hasActivePremium()이 구독
--    엔티티를 통째로 로딩하므로(join fetch s.plan), 이 환경변수가 채워진
--    환경에서 이 스크립트를 돌리면 구독자의 모든 프리미엄 요청이 500이
--    된다. 부하테스트 대상 환경(로컬/EC2 둘 다)에서 이 값이 비어 있는지
--    반드시 먼저 확인할 것 - 로컬은 보통 비어 있어 안전하지만, EC2는
--    실배포 설정이라 채워져 있을 가능성이 높다.
SET @N := 200;              -- 생성할 유저 수
SET @PREMIUM_N := 20;       -- 그중 ACTIVE 구독을 줄 유저 수
SET @CODES_PER_USER := 10;  -- 유저당 관심종목 수
SET @WATCHLIST_POOL := 50;  -- 관심종목을 뽑을 고정 풀 크기(전체 종목이 아님)

-- 0) 숫자 시퀀스 (MySQL 8 재귀 CTE)
DROP TEMPORARY TABLE IF EXISTS lt_seq;
CREATE TEMPORARY TABLE lt_seq (n INT PRIMARY KEY);
INSERT INTO lt_seq (n)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 1000)
SELECT n FROM s WHERE n <= @N;

-- 1) 유저 (uk_user_provider_provider_id로 중복 방지)
INSERT IGNORE INTO users (email, nickname, profile_image_url, provider, provider_id,
                          role, created_at, updated_at)
SELECT CONCAT('loadtest', n, '@quantlime.local'),
       CONCAT('부하테스트', n), NULL, 'GOOGLE', CONCAT('loadtest-', n),
       'USER', NOW(), NOW()
FROM lt_seq;

-- 2) 관심종목 그룹 (유저당 1개)
INSERT INTO watchlist_group (user_id, name, sort_order, created_at, updated_at)
SELECT u.user_id, '부하테스트', 0, NOW(), NOW()
FROM users u
WHERE u.provider_id LIKE 'loadtest-%'
  AND NOT EXISTS (SELECT 1 FROM watchlist_group g WHERE g.user_id = u.user_id);

-- 3) 관심종목
--    ⚠️ 고정된 소수(기본 50개) 종목 풀 안에서만 뽑는다. 유저마다 다른
--    종목을 주면 WatchlistedStockCodeCache가 수천 개로 부풀어 3초 릴레이
--    스케줄러가 매 틱마다 수천 번 convertAndSend를 돌게 된다(외부 호출은
--    안 늘지만 clientOutboundChannel 4스레드를 상시 점유해 측정 자체가
--    오염된다). load-test/scenarios/ws-stocks.js의 pickHot(stockCodes, 50)과
--    반드시 같은 풀(=정렬 순서 상위 50개)을 참조하도록 맞춰뒀다.
INSERT IGNORE INTO watchlist (user_id, stock_id, watchlist_group_id, sort_order,
                              created_at, updated_at)
SELECT u.user_id, s.stock_id, g.watchlist_group_id,
       ROW_NUMBER() OVER (PARTITION BY u.user_id ORDER BY s.stock_id),
       NOW(), NOW()
FROM users u
JOIN watchlist_group g ON g.user_id = u.user_id
JOIN (
  SELECT stock_id, ROW_NUMBER() OVER (ORDER BY stock_code) AS rn
  FROM stock
  WHERE market_type IN ('KOSPI', 'KOSDAQ') AND listing_status = 'LISTED'
    AND price_unsupported = 0
  LIMIT 50
) s ON s.rn <= @CODES_PER_USER
WHERE u.provider_id LIKE 'loadtest-%';

-- 4) ACTIVE 구독 (플랜은 SubscriptionPlanInitializer가 기동 시 시딩하는
--    PLAN_3M 재사용). uk_subscription_user 제약 때문에 유저당 1건만 가능.
--    auto_renew=0은 의도적 - 1이면 SubscriptionRenewalScheduler가 더미
--    빌링키로 실제 토스페이먼츠 결제 API를 호출 시도한다.
INSERT IGNORE INTO subscription (user_id, subscription_plan_id, status,
                                 current_period_start, current_period_end, next_billing_at,
                                 auto_renew, billing_key, installment_months,
                                 renewal_failure_count, created_at, updated_at)
SELECT u.user_id, p.subscription_plan_id, 'ACTIVE',
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL p.billing_period_months MONTH),
       DATE_ADD(CURDATE(), INTERVAL p.billing_period_months MONTH),
       0, 'loadtest-dummy-billing-key', 0, 0, NOW(), NOW()
FROM (SELECT user_id FROM users WHERE provider_id LIKE 'loadtest-%'
      ORDER BY user_id LIMIT 20) u
CROSS JOIN (SELECT subscription_plan_id, billing_period_months
            FROM subscription_plan WHERE code = 'PLAN_3M' LIMIT 1) p;

DROP TEMPORARY TABLE IF EXISTS lt_seq;

-- ─────────────────────────────────────────────────────────────────
-- 5) 텔레그램 다이제스트 + 소스 글 - TelegramFeedService.getDigests의
--    확정 N+1(countSourcePosts가 다이제스트 행마다 쿼리 1회, 심지어
--    카운트 하나 얻으려고 본문 전체를 로딩) 재현용. 실제 채널
--    (ChannelSeedInitializer가 시딩한 insidertracking/Donmaek)에
--    날짜별로 붙인다 - 신규 채널을 만들지 않고 기존 채널의 telegram_post를
--    이용해 실제 서비스와 같은 조회 경로를 그대로 탄다.
-- ─────────────────────────────────────────────────────────────────
SET @DIGEST_DAYS := 14;      -- 다이제스트를 만들 과거 일수(보존기간과 동일)
SET @POSTS_PER_DAY := 8;     -- 날짜당 시딩할 SELECTED 글 수

DROP TEMPORARY TABLE IF EXISTS lt_days;
CREATE TEMPORARY TABLE lt_days (d INT PRIMARY KEY);
INSERT INTO lt_days (d)
WITH RECURSIVE s(d) AS (SELECT 0 UNION ALL SELECT d + 1 FROM s WHERE d < 30)
SELECT d FROM s WHERE d < @DIGEST_DAYS;

DROP TEMPORARY TABLE IF EXISTS lt_post_seq;
CREATE TEMPORARY TABLE lt_post_seq (n INT PRIMARY KEY);
INSERT INTO lt_post_seq (n)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 20)
SELECT n FROM s WHERE n <= @POSTS_PER_DAY;

-- 소스 글 (external_post_id 유니크 제약으로 멱등)
INSERT IGNORE INTO telegram_post (channel_id, external_post_id, message_id, content,
                                  char_count, has_media, status, published_at,
                                  view_count, view_count_checked_at, created_at, updated_at)
SELECT c.channel_id,
       CONCAT('loadtest-', c.channel_id, '-', dd.d, '-', ps.n),
       900000000 + c.channel_id * 100000 + dd.d * 100 + ps.n,
       CONCAT('[부하테스트] ', c.name, ' 더미 글 ', dd.d, '-', ps.n,
              ' - N+1 재현용 시드 데이터. 실제 내용 아님.'),
       80, 0, 'SELECTED',
       DATE_ADD(CURDATE() - INTERVAL dd.d DAY, INTERVAL (9 + ps.n) HOUR),
       NULL, NULL, NOW(), NOW()
FROM channel c
CROSS JOIN lt_days dd
CROSS JOIN lt_post_seq ps
WHERE c.platform = 'TELEGRAM' AND c.enabled = 1;

-- 다이제스트 (uk_telegram_digest(channel_id, digest_date)로 멱등)
INSERT IGNORE INTO telegram_digest (channel_id, digest_date, model, payload,
                                    input_token, output_token, created_at, updated_at)
SELECT c.channel_id, CURDATE() - INTERVAL dd.d DAY, 'loadtest-seed',
       JSON_OBJECT(
         'summary', CONCAT('[부하테스트] ', c.name, ' ', dd.d, '일 전 더미 요약'),
         'key_points', JSON_ARRAY('더미 포인트 1', '더미 포인트 2'),
         'macro_points', JSON_ARRAY(),
         'caveat', '부하테스트 시드 데이터 - 실제 요약 아님'
       ),
       100, 200, NOW(), NOW()
FROM channel c
CROSS JOIN lt_days dd
WHERE c.platform = 'TELEGRAM' AND c.enabled = 1;

DROP TEMPORARY TABLE IF EXISTS lt_days;
DROP TEMPORARY TABLE IF EXISTS lt_post_seq;
