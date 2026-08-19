# QuantLime 부하테스트 (k6)

전체 가이드는 [`docs/LOAD_TESTING.md`](../docs/LOAD_TESTING.md) 참고.
이 파일은 디렉터리 구조만 요약한다.

```
load-test/
├─ config/       설정(env.example → env로 복사해서 채울 것, gitignore)
├─ lib/          공용 라이브러리(guard.js가 유일한 HTTP 진입점)
├─ data/         종목코드/검색어 풀, 토큰(tokens.json은 gitignore)
├─ scenarios/    k6 실행 시나리오
├─ seed/         테스트 데이터 시딩/제거, 토큰 발급
└─ run/          실행 러너, 안전 검사, Grafana 어노테이션
```

빠른 시작(로컬, 8081 대상):

```bash
cp load-test/config/env.example load-test/config/env
# load-test/config/env 편집 후
set -a && source load-test/config/env && set +a
set -a && source backend/.env && set +a   # JWT_SECRET 확보

make load-seed
make load-smoke
```
