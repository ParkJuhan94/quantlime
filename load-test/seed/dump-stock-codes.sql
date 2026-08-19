-- VU가 사용할 실제 종목코드 풀을 JSON 배열 하나로 덤프한다.
-- seed.sh가 이 출력을 load-test/data/stock-codes.json으로 저장한다.
-- 국내 상장·시세조회 가능 종목만(price_unsupported=0) 대상 - 공개 정보라
-- 결과 파일은 커밋 가능하다.
SET SESSION group_concat_max_len = 1000000;
SELECT JSON_ARRAYAGG(stock_code) AS codes
FROM (
  SELECT stock_code
  FROM stock
  WHERE market_type IN ('KOSPI', 'KOSDAQ')
    AND listing_status = 'LISTED'
    AND price_unsupported = 0
  ORDER BY stock_code
) t;
