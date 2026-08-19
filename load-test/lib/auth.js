// seed/mint-tokens.mjs가 만든 load-test/data/tokens.json을 읽어 VU에 배정한다.
import { SharedArray } from 'k6/data';

// [{ userId, premium, token }, ...]
const tokens = new SharedArray('tokens', () => JSON.parse(open('../data/tokens.json')));

const premiumTokens = new SharedArray('tokens-premium', () =>
  JSON.parse(open('../data/tokens.json')).filter((t) => t.premium)
);

// VU 하나가 항상 같은 사용자를 쓰게 한다 - iteration마다 사용자가 바뀌면
// 관심종목/구독 조회 패턴이 실제 트래픽과 달라진다.
export function userForVu() {
  if (tokens.length === 0) {
    throw new Error('[auth] tokens.json이 비어 있다 - seed/seed.sh를 먼저 실행할 것');
  }
  return tokens[(__VU - 1) % tokens.length];
}

export function premiumUserForVu() {
  if (premiumTokens.length === 0) {
    throw new Error('[auth] 구독(premium) 토큰이 없다 - seed/seed.sh가 구독을 시딩했는지 확인');
  }
  return premiumTokens[(__VU - 1) % premiumTokens.length];
}

export function bearer(user) {
  return { Authorization: `Bearer ${user.token}` };
}
