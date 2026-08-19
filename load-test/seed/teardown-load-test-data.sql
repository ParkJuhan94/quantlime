-- load-test/seed/seed-load-test-data.sql이 심은 시드 데이터를 전량
-- 제거한다. provider_id LIKE 'loadtest-%' / external_post_id LIKE
-- 'loadtest-%'로 식별 가능한 행만 지운다 - 실제 서비스 데이터는 건드리지
-- 않는다. FK가 NO_CONSTRAINT(프로젝트 관례)라 자식 테이블부터 순서대로
-- 삭제해야 한다.

DELETE w FROM watchlist w JOIN users u ON u.user_id = w.user_id
  WHERE u.provider_id LIKE 'loadtest-%';

DELETE g FROM watchlist_group g JOIN users u ON u.user_id = g.user_id
  WHERE u.provider_id LIKE 'loadtest-%';

DELETE s FROM subscription s JOIN users u ON u.user_id = s.user_id
  WHERE u.provider_id LIKE 'loadtest-%';

DELETE FROM users WHERE provider_id LIKE 'loadtest-%';

-- 텔레그램 N+1 재현용 시드. 다이제스트가 소스 글을 FK가 아니라
-- (channel, published_at 범위)로 재조회하는 구조라 둘을 독립적으로
-- external_post_id / digest_date 패턴으로 식별해 지운다.
DELETE FROM telegram_digest_ticker WHERE telegram_digest_id IN (
  SELECT telegram_digest_id FROM telegram_digest WHERE model = 'loadtest-seed'
);
DELETE FROM telegram_digest WHERE model = 'loadtest-seed';
DELETE FROM telegram_post WHERE external_post_id LIKE 'loadtest-%';
