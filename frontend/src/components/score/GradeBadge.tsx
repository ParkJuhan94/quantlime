// 백엔드 ScoreResponse.grade는 enum 이름(SSS 등)이 아니라 한글 표시명
// (Grade.label)으로 내려온다 - com.quantlab.score.domain.Grade 참고.
const GRADE_STYLES: Record<string, string> = {
  최우수: 'bg-purple-100 text-purple-800',
  '매우 우수': 'bg-indigo-100 text-indigo-800',
  우수: 'bg-blue-100 text-blue-800',
  양호: 'bg-green-100 text-green-800',
  보통: 'bg-yellow-100 text-yellow-800',
  주의: 'bg-orange-100 text-orange-800',
  위험: 'bg-red-100 text-red-800',
}

export function GradeBadge({ grade }: { grade: string | null }) {
  if (!grade) {
    return <span className="text-xs text-gray-400">등급 없음</span>
  }
  const style = GRADE_STYLES[grade] ?? 'bg-gray-100 text-gray-800'
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>{grade}</span>
}
