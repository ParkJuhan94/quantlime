// 커뮤니티 피드(게시판)는 이번 홈 리디자인 범위 밖 - docs/ROADMAP.md #4
// 참고(기술 난이도는 낮지만 범위가 커서 별도 착수 필요). 디자인 시안 자체가
// 아직 만들지 않은 화면에 이 "준비 중" 패턴을 쓰고 있어 그대로 재사용했다.
export function FeedPlaceholderPage() {
  return (
    <div className="flex h-[420px] flex-col items-center justify-center gap-2 text-gray-400">
      <p className="text-sm font-semibold">피드 — 준비 중이에요</p>
      <p className="text-xs">곧 종목 토론 게시판이 추가될 예정입니다.</p>
    </div>
  )
}
