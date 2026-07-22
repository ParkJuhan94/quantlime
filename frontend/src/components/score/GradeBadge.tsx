// 백엔드 ScoreResponse.grade는 enum 이름이 아니라 한글 표시명(Grade.label)
// 으로 내려온다 - com.quantlime.score.domain.Grade 참고(5단계: 강력매도~강력매수).
// RankingTable처럼 스코어 숫자와 등급을 한 배지로 합쳐 보여주는 곳에서도
// 동일한 색을 쓰도록 export한다(2026-07-17 - 종목상세 ScoreSummaryRow와
// 톤을 맞추기 위해 새로 필요해짐).
export const GRADE_STYLES: Record<string, string> = {
  강력매수: 'bg-red-600 text-white',
  매수: 'bg-red-100 text-red-700',
  중립: 'bg-gray-200 text-gray-700',
  매도: 'bg-blue-100 text-blue-700',
  강력매도: 'bg-blue-600 text-white',
}

// 라벨 글자수가 등급마다 다르다(강력매도/강력매수=4자, 매수/매도=2자,
// 중립=2자) - px로 여백만 주면 랭킹 표에서 열마다 박스 폭이 들쭉날쭉해
// 보인다. 고정 너비 박스로 통일해 어느 등급이 와도 같은 모양을 유지한다
// (2026-07-16, "박스 모양 일관성 있게" 피드백).
export function GradeBadge({ grade }: { grade: string | null }) {
  if (!grade) {
    return <span className="inline-flex w-16 justify-center rounded-md py-0.5 text-xs text-gray-400">-</span>
  }
  const style = GRADE_STYLES[grade] ?? 'bg-gray-100 text-gray-800'
  return <span className={`inline-flex w-16 justify-center rounded-md py-0.5 text-xs font-semibold ${style}`}>{grade}</span>
}
