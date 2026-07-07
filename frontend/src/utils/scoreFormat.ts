// 백엔드 스코어는 소수점 10자리 이상의 원시 double로 내려온다
// (예: 43.91018446890717). 화면엔 소수 1자리로 반올림해 보여준다 -
// 반올림 안 하면 보기 안 좋을 뿐 아니라, 랭킹 테이블에서는 긴 숫자가
// 컬럼 폭을 밀어내 좁은 화면에서 가로 스크롤을 유발하기도 했다.
export function formatScore(score: number | null | undefined): string {
  return score != null ? score.toFixed(1) : '-'
}
